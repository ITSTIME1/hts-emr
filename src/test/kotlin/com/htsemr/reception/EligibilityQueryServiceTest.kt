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

/**
 * 현재 자격 조회(currentEligibility) 테스트.
 *
 * 내원 건은 실제 흐름대로 registerVisit으로 만든다. registerVisit은 자기 트랜잭션을 커밋하므로,
 * @Transactional 롤백에 기대지 않고 테스트가 끝날 때마다 직접 지운다.
 * 외부 기관은 모의 구현(FakeEligibilityProvider)이 그대로 응답한다(건강보험, 50%).
 */
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

    /** 자격 기록이 내원 건을 외래 키로 가리키므로 자격 기록부터 지운다. */
    @AfterEach
    fun cleanUp() {
        eligibilityRepository.deleteAll()
        visitRepository.deleteAll()
    }

    @Nested
    inner class `currentEligibility는` {

        @Test
        fun `접수 직후에는 첫 자격 기록(seq 1)을 돌려준다`() {
            // 모의 외부 기관의 응답(NATIONAL_HEALTH, 50)과 visitId가 그대로 나오는지 본다.
            val eligibility = eligibilityQueryService.currentEligibility(visitId = visitId)
            assertThat(eligibility.insuranceType).isEqualTo(InsuranceType.NATIONAL_HEALTH)
            assertThat(eligibility.copayRate).isEqualTo(50)
        }

        @Test
        fun `자격 기록이 여럿이면 순번이 가장 큰 기록을 돌려준다`() {
            // 소급 변경 기능이 아직 없으므로 eligibilityRepository로 seq 2를 직접 저장한다.


            eligibilityRepository.save(
                Eligibility(
                    visitId = visitId,
                    seq = 2, // 수동으로 데이터를 넣어줄 때, seq를 증가해야 2건의 자격 기록을 만들 수 있음.
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
