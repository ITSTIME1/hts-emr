package com.htsemr.reception

import com.htsemr.TestcontainersConfiguration
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

/**
 * 내원 건 조회(visit) 테스트.
 *
 * 내원 건은 실제 흐름대로 registerVisit으로 만든다. registerVisit은 자기 트랜잭션을 커밋하므로,
 * @Transactional 롤백에 기대지 않고 테스트가 끝날 때마다 직접 지운다.
 * 외부 기관은 모의 구현(FakeEligibilityProvider)이 그대로 응답한다.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class VisitQueryServiceTest @Autowired constructor(
    private val visitQueryService: VisitQueryService,
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
    inner class `visit은` {

        @Test
        fun `접수한 값을 그대로 돌려준다`() {
            // VisitView(id, 환자, 진료과, 진료 의사, 진료일)가 접수 입력과 같은지 본다.

            val visitView = visitQueryService.visit(visitId = visitId)
            assertThat(visitView.id).isEqualTo(visitId)
            assertThat(visitView.patientNo).isEqualTo("T-P001")
            assertThat(visitView.departmentCode).isEqualTo("T-IM")
            assertThat(visitView.visitDate).isEqualTo(LocalDate.of(2026, 6, 2))
        }

        @Test
        fun `없는 내원 건이면 VisitNotFoundException이 발생한다`() {
            val invalidVisitId = visitId + 1
            // 예외에 조회한 visitId가 담기는지도 본다.
            assertThrows<VisitNotFoundException> {
                visitQueryService.visit(visitId = invalidVisitId)
            }
        }
    }
}
