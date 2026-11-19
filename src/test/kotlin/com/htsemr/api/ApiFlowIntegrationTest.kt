package com.htsemr.api

import com.htsemr.TestcontainersConfiguration
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
internal class ApiFlowIntegrationTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val jdbcTemplate: JdbcTemplate,
) {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @AfterEach
    fun cleanUp() {
        listOf(
            "payment_line", "payment_item", "payment",
            "invoice_line", "invoice",
            "order_item_cancellation", "order_item", "medical_order",
            "eligibility", "visit",
        ).forEach { jdbcTemplate.update("delete from $it") }
    }

    @Test
    fun `TC-AI-01 같은 접수를 두 번 보내면 201 다음 409 DUPLICATE_VISIT이다`() {
        val patientNo = newPatientNo()

        postJson("/api/v1/visits", registerBody(patientNo, "IM")).andExpect {
            status { isCreated() }
        }
        postJson("/api/v1/visits", registerBody(patientNo, "IM")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE_VISIT") }
        }
    }

    @Test
    fun `TC-AI-02 같은 날 다른 진료과로 접수하면 별개의 접수로 201이다`() {
        val patientNo = newPatientNo()

        postJson("/api/v1/visits", registerBody(patientNo, "IM")).andExpect { status { isCreated() } }
        postJson("/api/v1/visits", registerBody(patientNo, "OS")).andExpect { status { isCreated() } }
    }

    @Test
    fun `TC-AI-03 접수에서 오더, 산정, 현금 수납 완료까지 한 흐름이 이어진다`() {
        val visitId = id(postJson("/api/v1/visits", registerBody(newPatientNo(), "IM")).andReturn(), "$.visitId")

        mockMvc.get("/api/v1/visits/$visitId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(visitId) }
            jsonPath("$.visitDate") { value(VISIT_DATE) }
        }
        mockMvc.get("/api/v1/visits/$visitId/eligibility").andExpect {
            status { isOk() }
            jsonPath("$.insuranceType") { value("NATIONAL_HEALTH") }
            jsonPath("$.seq") { value(1) }
        }

        val cbc = feeItemId("EX-CBC")
        val cert = feeItemId("EX-NC-CERT")
        postJson(
            "/api/v1/visits/$visitId/orders",
            """{"doctorNo":"D0001","orderItems":[
                {"feeItemId":$cbc,"quantity":2},
                {"feeItemId":$cert,"quantity":1}
            ]}""",
        ).andExpect {
            status { isCreated() }
            header { string("Location", "/api/v1/visits/$visitId/order-items") }
        }

        mockMvc.get("/api/v1/visits/$visitId/order-items").andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].status") { value("ORDERED") }
        }

        val invoiceId = id(postJson("/api/v1/visits/$visitId/invoices").andReturn(), "$.invoiceId")
        mockMvc.get("/api/v1/invoices/$invoiceId").andExpect {
            status { isOk() }
            jsonPath("$.visitId") { value(visitId) }
            jsonPath("$.lines.length()") { value(2) }
            jsonPath("$.coveredCopay") { value(5000) }
            jsonPath("$.nonCoveredTotal") { value(20000) }
            jsonPath("$.patientAmount") { value(25000) }
        }

        val counterId = counterId("C01")
        val first = postJson(
            "/api/v1/invoices/$invoiceId/cash-payments",
            cashBody(counterId, received = 10000, applied = 10000),
        ).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_PROGRESS") }
            jsonPath("$.remaining") { value(15000) }
        }.andReturn()
        val paymentId = id(first, "$.paymentId")

        postJson(
            "/api/v1/invoices/$invoiceId/cash-payments",
            cashBody(counterId, received = 20000, applied = 15000),
        ).andExpect {
            status { isOk() }
            jsonPath("$.paymentId") { value(paymentId) }
            jsonPath("$.status") { value("COMPLETED") }
        }

        mockMvc.get("/api/v1/payments/$paymentId").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.patientAmount") { value(25000) }
            jsonPath("$.paidAmount") { value(25000) }
            jsonPath("$.lines.length()") { value(2) }
            jsonPath("$.lines[1].change") { value(5000) }
        }

        postJson("/api/v1/visits/$visitId/invoices").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("NOTHING_TO_PAY") }
        }
        postJson(
            "/api/v1/invoices/$invoiceId/cash-payments",
            cashBody(counterId, received = 1000, applied = 1000),
        ).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("INVOICE_ALREADY_USED") }
        }
    }

    @Test
    fun `TC-AI-04 컨텍스트 전체에서도 검증 실패는 400 VALIDATION_FAILED이다`() {
        postJson("/api/v1/visits", """{"patientNo":"","departmentCode":"IM","doctorNo":"D0001","visitDate":"$VISIT_DATE"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("patientNo") }
            }
    }

    @Test
    fun `TC-AI-05 없는 경로는 404이고 code가 있다`() {
        mockMvc.get("/api/v1/nothing").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `TC-AI-06 없는 접수의 자격과 산정 요청은 404 VISIT_NOT_FOUND이다`() {
        mockMvc.get("/api/v1/visits/999999/eligibility").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VISIT_NOT_FOUND") }
        }
        postJson("/api/v1/visits/999999/invoices").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VISIT_NOT_FOUND") }
        }
    }

    @Test
    fun `TC-AI-07 한글 200자 취소 사유도 취소된다`() {
        val visitId = id(postJson("/api/v1/visits", registerBody(newPatientNo(), "IM")).andReturn(), "$.visitId")
        postJson(
            "/api/v1/visits/$visitId/orders",
            """{"doctorNo":"D0001","orderItems":[{"feeItemId":${feeItemId("EX-CBC")},"quantity":1}]}""",
        ).andExpect { status { isCreated() } }
        val orderItemId = id(mockMvc.get("/api/v1/visits/$visitId/order-items").andReturn(), "$.items[0].id")

        val reason = "가".repeat(200)
        postJson(
            "/api/v1/order-items/$orderItemId/cancellation",
            """{"doctorNo":"D0001","reason":"$reason"}""",
        ).andExpect {
            status { isNoContent() }
        }
    }

    private fun postJson(path: String, body: String? = null) = mockMvc.post(path) {
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
    }

    private fun id(result: MvcResult, path: String): Long =
        JsonPath.read<Number>(result.response.contentAsString, path).toLong()

    private fun registerBody(patientNo: String, departmentCode: String) =
        """{"patientNo":"$patientNo","departmentCode":"$departmentCode","doctorNo":"D0001","visitDate":"$VISIT_DATE"}"""

    private fun cashBody(counterId: Long, received: Long, applied: Long) =
        """{"counterId":$counterId,"cashierNo":"C0001","receivedAmount":$received,"appliedAmount":$applied}"""

    private fun newPatientNo() = "IT-" + UUID.randomUUID().toString().take(12)

    private fun feeItemId(code: String): Long =
        jdbcTemplate.queryForObject("select id from fee_item where code = ?", Long::class.javaObjectType, code)!!

    private fun counterId(code: String): Long =
        jdbcTemplate.queryForObject("select id from counter where code = ?", Long::class.javaObjectType, code)!!

    private companion object {
        const val VISIT_DATE = "2026-11-19"
    }
}
