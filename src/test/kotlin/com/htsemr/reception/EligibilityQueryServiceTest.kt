package com.htsemr.reception

import com.htsemr.TestcontainersConfiguration
import com.htsemr.reception.entity.Eligibility
import com.htsemr.reception.repository.EligibilityRepository
import com.htsemr.reception.repository.VisitRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.LocalDate
import java.time.LocalDateTime

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class EligibilityQueryServiceTest @Autowired constructor(
    private val eligibilityQueryService: EligibilityQueryService,
    private val registrationService: RegistrationService,
    private val visitRepository: VisitRepository,
    private val eligibilityRepository: EligibilityRepository,
) {

    private var visitId: Long = 0

    @BeforeEach
    fun registerVisit() {
        visitId = registrationService.registerVisit(
            patientNo = "T-P001",
            departmentCode = "T-IM",
            doctorNo = "T-D001",
            visitDate = LocalDate.of(2026, 6, 2),
        )
    }

    @AfterEach
    fun cleanUp() {
        eligibilityRepository.deleteAll()
        visitRepository.deleteAll()
    }

    @Nested
    inner class `currentEligibility는` {

        @Test
        fun `접수 직후에는 첫 자격 기록(seq 1)을 돌려준다`() {
            val eligibility = eligibilityQueryService.currentEligibility(visitId = visitId)
            assertThat(eligibility.insuranceType).isEqualTo(InsuranceType.NATIONAL_HEALTH)
            assertThat(eligibility.copayRate).isEqualTo(50)
        }

        @Test
        fun `자격 기록이 여럿이면 순번이 가장 큰 기록을 돌려준다`() {

            eligibilityRepository.save(
                Eligibility(
                    visitId = visitId,
                    seq = 2,
                    insuranceType = InsuranceType.NATIONAL_HEALTH,
                    copayRate = 50,
                    recordedAt = LocalDateTime.now(),
                )
            )

            val eligibility = eligibilityQueryService.currentEligibility(visitId = visitId)
            assertThat(eligibility.seq).isEqualTo(2)
        }

        @Test
        fun `없는 내원 건이면 VisitNotFoundException이 발생한다`() {
            val invalidVisitId = visitId + 1
            assertThrows<VisitNotFoundException> {
                eligibilityQueryService.currentEligibility(visitId = invalidVisitId)
            }
        }
    }
}
