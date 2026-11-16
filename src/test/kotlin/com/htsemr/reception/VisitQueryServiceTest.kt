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

    @AfterEach
    fun cleanUp() {
        eligibilityRepository.deleteAll()
        visitRepository.deleteAll()
    }

    @Nested
    inner class `visit은` {

        @Test
        fun `접수한 값을 그대로 돌려준다`() {

            val visitView = visitQueryService.visit(visitId = visitId)
            assertThat(visitView.id).isEqualTo(visitId)
            assertThat(visitView.patientNo).isEqualTo("T-P001")
            assertThat(visitView.departmentCode).isEqualTo("T-IM")
            assertThat(visitView.visitDate).isEqualTo(LocalDate.of(2026, 6, 2))
        }

        @Test
        fun `없는 내원 건이면 VisitNotFoundException이 발생한다`() {
            val invalidVisitId = visitId + 1
            assertThrows<VisitNotFoundException> {
                visitQueryService.visit(visitId = invalidVisitId)
            }
        }
    }
}
