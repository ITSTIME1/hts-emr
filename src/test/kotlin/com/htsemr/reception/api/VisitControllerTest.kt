package com.htsemr.reception.api

import com.htsemr.api.ApiExceptionHandler
import com.htsemr.reception.DuplicateVisitException
import com.htsemr.reception.EligibilityQueryService
import com.htsemr.reception.EligibilityView
import com.htsemr.reception.InsuranceType
import com.htsemr.reception.RegistrationService
import com.htsemr.reception.VisitNotFoundException
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDate
import java.time.LocalDateTime

class VisitControllerTest {

    private val registrationService = mock(RegistrationService::class.java)
    private val visitQueryService = mock(VisitQueryService::class.java)
    private val eligibilityQueryService = mock(EligibilityQueryService::class.java)

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(VisitController(registrationService, visitQueryService, eligibilityQueryService))
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    @Test
    fun `TC-A-01 정상 접수는 201과 Location, visitId를 돌려준다`() {
        val visitDate = LocalDate.of(2026, 11, 19)
        given(registrationService.registerVisit("P0001", "IM", "D0001", visitDate)).willReturn(57L)

        registerVisit("""
            {
                "patientNo":"P0001",
                "departmentCode":"IM",
                "doctorNo":"D0001",
                "visitDate":"2026-11-19"
            }
            """.trimIndent())
            .andExpect {
                status { isCreated() }
                header { string("Location", endsWith("/api/v1/visits/57")) }
                jsonPath("$.visitId") { value(57) }
            }
    }

    @Test
    fun `TC-A-06 같은 접수를 다시 보내면 409 DUPLICATE_VISIT`() {
        val visitDate = LocalDate.of(2026, 11, 19)
        given(registrationService.registerVisit("P0001", "IM", "D0001", visitDate))
            .willThrow(DuplicateVisitException("P0001", visitDate, "IM"))

        mockMvc.post("/api/v1/visits") {
            contentType = MediaType.APPLICATION_JSON
            // patient_no, visit_date, department_code
            content = """{"patientNo":"P0001","departmentCode":"IM","doctorNo":"D0001","visitDate":"2026-11-19"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE_VISIT") }
        }
    }

    @Test
    fun `TC-A-07 예상 못 한 예외는 500 INTERNAL_ERROR이고 원인을 본문에 노출하지 않는다`() {
        val visitDate = LocalDate.of(2026, 11, 19)
        given(registrationService.registerVisit("P0001", "IM", "D0001", visitDate))
            .willThrow(IllegalStateException("ORA-12899 VISIT.PATIENT_NO 내부 정보"))

        mockMvc.post("/api/v1/visits") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"patientNo":"P0001","departmentCode":"IM","doctorNo":"D0001","visitDate":"2026-11-19"}"""
        }.andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            content { string(not(containsString("ORA-12899"))) }
        }
    }

    private fun registerVisit(body: String) = mockMvc.post("/api/v1/visits") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `TC-A-02 patientNo가 빈 값이면 400 VALIDATION_FAILED이고 서비스를 부르지 않는다`() {
        registerVisit("""{"patientNo":"","departmentCode":"IM","doctorNo":"D0001","visitDate":"2026-11-19"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("patientNo") }
            }

        verifyNoInteractions(registrationService)
    }

    @Test
    fun `TC-A-03 patientNo가 21자이면 400 VALIDATION_FAILED`() {
        val tooLong = "P".repeat(21)

        registerVisit("""{"patientNo":"$tooLong","departmentCode":"IM","doctorNo":"D0001","visitDate":"2026-11-19"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("patientNo") }
                jsonPath("$.errors[0].reason") { value("최대 20자입니다") }
            }

        verifyNoInteractions(registrationService)
    }

    @Test
    fun `TC-A-04 필수 필드가 빠지면 400 MALFORMED_REQUEST`() {
        registerVisit("""{"patientNo":"P0001","departmentCode":"IM","visitDate":"2026-11-19"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("MALFORMED_REQUEST") }
            }

        verifyNoInteractions(registrationService)
    }

    @Test
    fun `TC-A-05 visitDate가 날짜 형식이 아니면 400 MALFORMED_REQUEST`() {
        registerVisit("""{"patientNo":"P0001","departmentCode":"IM","doctorNo":"D0001","visitDate":"2026-13-45"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("MALFORMED_REQUEST") }
            }

        verifyNoInteractions(registrationService)
    }

    @Test
    fun `TC-A-08 존재하는 접수는 200과 접수 정보를 돌려준다`() {
        given(visitQueryService.visit(57L)).willReturn(
            VisitView(57L, "P0001", "IM", "D0001", LocalDate.of(2026, 11, 19)),
        )

        mockMvc.get("/api/v1/visits/57").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(57) }
            jsonPath("$.patientNo") { value("P0001") }
            jsonPath("$.departmentCode") { value("IM") }
            jsonPath("$.doctorNo") { value("D0001") }
            jsonPath("$.visitDate") { value("2026-11-19") }
        }
    }

    @Test
    fun `TC-A-09 없는 접수는 404 VISIT_NOT_FOUND`() {
        given(visitQueryService.visit(999L)).willThrow(VisitNotFoundException(999L))

        mockMvc.get("/api/v1/visits/999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VISIT_NOT_FOUND") }
        }
    }

    @Test
    fun `TC-A-10 접수 ID가 숫자가 아니면 400 BAD_REQUEST`() {
        mockMvc.get("/api/v1/visits/abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BAD_REQUEST") }
        }
        verifyNoInteractions(visitQueryService)
    }

    @Test
    fun `TC-A-11 지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED`() {
        mockMvc.put("/api/v1/visits").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("METHOD_NOT_ALLOWED") }
        }
        verifyNoInteractions(visitQueryService)
    }

    @Test
    fun `TC-A-12 존재하는 접수의 현재 자격은 200과 자격 정보를 돌려준다`() {
        given(eligibilityQueryService.currentEligibility(57L)).willReturn(
            EligibilityView(
                visitId = 57L,
                seq = 2,
                insuranceType = InsuranceType.NATIONAL_HEALTH,
                copayRate = 30,
                recordedAt = LocalDateTime.of(2026, 11, 19, 10, 30, 0),
            ),
        )

        mockMvc.get("/api/v1/visits/57/eligibility").andExpect {
            status { isOk() }
            jsonPath("$.visitId") { value(57) }
            jsonPath("$.seq") { value(2) }
            jsonPath("$.insuranceType") { value("NATIONAL_HEALTH") }
            jsonPath("$.copayRate") { value(30) }
            jsonPath("$.recordedAt") { value("2026-11-19T10:30:00") }
        }
    }

    @Test
    fun `TC-A-13 없는 접수의 자격 조회는 404 VISIT_NOT_FOUND`() {
        given(eligibilityQueryService.currentEligibility(999L)).willThrow(VisitNotFoundException(999L))

        mockMvc.get("/api/v1/visits/999/eligibility").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VISIT_NOT_FOUND") }
        }
    }

    @Test
    fun `TC-A-14 자격 조회의 접수 ID가 숫자가 아니면 400 BAD_REQUEST`() {
        mockMvc.get("/api/v1/visits/abc/eligibility").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BAD_REQUEST") }
        }
        verifyNoInteractions(eligibilityQueryService)
    }
}
