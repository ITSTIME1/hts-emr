package com.htsemr.reception

import com.htsemr.TestcontainersConfiguration
import com.htsemr.reception.external.EligibilityProvider
import com.htsemr.reception.external.EligibilityResponse
import com.htsemr.reception.repository.EligibilityRepository
import com.htsemr.reception.repository.VisitRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class RegistrationServiceTest @Autowired constructor(
    private val registrationService: RegistrationService,
    private val visitRepository: VisitRepository,
    private val eligibilityRepository: EligibilityRepository,
) {

    @MockitoBean
    private lateinit var eligibilityProvider: EligibilityProvider

    @BeforeEach
    fun stubEligibility() {
        given(eligibilityProvider.inquireEligibility("T-P001", LocalDate.of(2026, 6, 2)))
            .willReturn(EligibilityResponse(InsuranceType.NATIONAL_HEALTH, 50))
    }

    @AfterEach
    fun cleanUp() {
        eligibilityRepository.deleteAll()
        visitRepository.deleteAll()
    }

    @Nested
    inner class `정상 접수는` {

        @Test
        fun `새 내원 건의 id를 돌려준다`() {
            val visitId: Long = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            val saved = visitRepository.findById(visitId)

            assertThat(saved).isPresent
            assertThat(saved.get().patientNo).isEqualTo("T-P001")
        }

        @Test
        fun `내원 건을 입력값 그대로 저장한다`() {
            val visitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            val visit = visitRepository.findById(visitId).get()

            assertThat(visit.patientNo).isEqualTo("T-P001")
            assertThat(visit.departmentCode).isEqualTo("T-IM")
            assertThat(visit.doctorNo).isEqualTo("T-D001")
            assertThat(visit.visitDate).isEqualTo(LocalDate.of(2026, 6, 2))
            assertThat(visit.registeredAt).isNotNull()
        }

        @Test
        fun `외부 기관이 알려 준 자격으로 첫 자격 기록(seq 1)을 남긴다`() {
            val visitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )
            val eligibility = eligibilityRepository.findFirstByVisitIdOrderBySeqDesc(visitId)!!
            assertThat(eligibility.seq).isEqualTo(1)
            assertThat(eligibility.insuranceType).isEqualTo(InsuranceType.NATIONAL_HEALTH)
            assertThat(eligibility.copayRate).isEqualTo(50)

        }

        @Test
        fun `내원 건의 접수 시각과 자격 기록의 기록 시각이 같다`() {
            val visitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            val visit = visitRepository.findById(visitId).get()
            val eligibility = eligibilityRepository.findFirstByVisitIdOrderBySeqDesc(visitId)!!
            assertThat(visit.registeredAt).isEqualTo(eligibility.recordedAt)
        }
    }

    @Nested
    inner class `중복 접수는` {

        @Test
        fun `같은 환자·진료일·진료과면 DuplicateVisitException이 발생한다`() {
            val visitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )
            assertThrows<DuplicateVisitException> {
                registrationService.registerVisit(
                    patientNo = "T-P001",
                    departmentCode = "T-IM",
                    doctorNo = "T-D001",
                    visitDate = LocalDate.of(2026, 6, 2),
                )
            }
        }

        @Test
        fun `실패해도 처음 접수한 내원 건과 자격 기록만 남는다`() {

            val visitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            assertThrows<DuplicateVisitException> {
                registrationService.registerVisit(
                    patientNo = "T-P001",
                    departmentCode = "T-IM",
                    doctorNo = "T-D001",
                    visitDate = LocalDate.of(2026, 6, 2),
                )
            }

            assertThat(visitRepository.findAll()).extracting<Long> { it.id }.containsExactly(visitId)
            val eligibilities = eligibilityRepository.findAll()
            assertThat(eligibilities).hasSize(1)
            assertThat(eligibilities[0].visitId).isEqualTo(visitId)
            assertThat(eligibilities[0].seq).isEqualTo(1)

        }

        @Test
        fun `진료과가 다르면 각각 접수되고 각자 seq 1을 가진다`() {
            val firstVisitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            val secondVisitId = registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IH",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            val firstVisit = visitRepository.findById(firstVisitId).get()
            val secondVisit = visitRepository.findById(secondVisitId).get()

            assertThat(firstVisit.id).isEqualTo(firstVisitId)
            assertThat(secondVisit.id).isEqualTo(secondVisitId)

            val firstEligibility = eligibilityRepository.findFirstByVisitIdOrderBySeqDesc(firstVisitId)!!
            val secondEligibility = eligibilityRepository.findFirstByVisitIdOrderBySeqDesc(secondVisitId)!!

            assertThat(firstEligibility.seq).isEqualTo(1)
            assertThat(secondEligibility.seq).isEqualTo(1)

        }
    }

    @Nested
    inner class `저장 중 실패하면` {

        @BeforeEach
        fun stubInvalidEligibility() {
            given(eligibilityProvider.inquireEligibility("T-P001", LocalDate.of(2026, 6, 2)))
                .willReturn(EligibilityResponse(InsuranceType.UNINSURED, 50))

        }

        @Test
        fun `내원 건도 함께 롤백되어 아무것도 남지 않는다`() {
            assertThrows<DataIntegrityViolationException> {
                registrationService.registerVisit(
                    patientNo = "T-P001",
                    departmentCode = "T-IM",
                    doctorNo = "T-D001",
                    visitDate = LocalDate.of(2026, 6, 2),
                )
            }

            assertThat(visitRepository.count()).isZero()
            assertThat(eligibilityRepository.count()).isZero()

        }

        @Test
        fun `업무 예외로 바꾸지 않고 원래 예외를 그대로 던진다`() {
            val exception = assertThrows<DataIntegrityViolationException> {
                registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            ) }

            assertThat(exception).isNotInstanceOf(DuplicateVisitException::class.java)
            assertThat(exception).hasStackTraceContaining("CK_ELIGIBILITY_UNINSURED_RATE")
        }

        @Test
        fun `제약 위반이 아닌 오류(길이 초과)도 그대로 던진다`() {
            val exception = assertThrows<DataAccessException> {
                registrationService.registerVisit(
                    patientNo = "T-" + "P".repeat(19),
                    departmentCode = "T-IM",
                    doctorNo = "T-D001",
                    visitDate = LocalDate.of(2026, 6, 2),
                )
            }

            assertThat(exception).hasStackTraceContaining("ORA-12899")
        }
    }

    @Nested
    inner class `외부 기관 조회는` {

        @Test
        fun `트랜잭션 밖에서 호출된다`() {
            var transactionActiveDuringInquiry: Boolean? = null

            given(eligibilityProvider.inquireEligibility("T-P001", LocalDate.of(2026, 6, 2))).willAnswer {
                transactionActiveDuringInquiry = TransactionSynchronizationManager.isActualTransactionActive()
                EligibilityResponse(InsuranceType.NATIONAL_HEALTH, 50)
            }

            registrationService.registerVisit(
                patientNo = "T-P001",
                departmentCode = "T-IM",
                doctorNo = "T-D001",
                visitDate = LocalDate.of(2026, 6, 2),
            )

            assertThat(transactionActiveDuringInquiry).isFalse()
        }

        @Test
        fun `실패하면 내원 건도 자격 기록도 남지 않는다`() {
            given(eligibilityProvider.inquireEligibility("T-P001", LocalDate.of(2026, 6, 2)))
                .willThrow(IllegalStateException("자격 조회 실패"))

            assertThrows<IllegalStateException> {
                registrationService.registerVisit(
                    patientNo = "T-P001",
                    departmentCode = "T-IM",
                    doctorNo = "T-D001",
                    visitDate = LocalDate.of(2026, 6, 2),
                )
            }

            assertThat(visitRepository.count()).isZero()
            assertThat(eligibilityRepository.count()).isZero()

        }
    }
}
