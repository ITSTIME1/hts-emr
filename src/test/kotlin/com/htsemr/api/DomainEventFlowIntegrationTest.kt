package com.htsemr.api

import com.htsemr.TestcontainersConfiguration
import com.htsemr.observability.BusinessKeys
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
internal class DomainEventFlowIntegrationTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val jdbcTemplate: JdbcTemplate,
    private val requestLogFilter: RequestLogFilter,
) {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(requestLogFilter)
            .build()
    }

    @AfterEach
    fun cleanUp() {
        MDC.clear()
        listOf(
            "payment_line", "payment_item", "payment",
            "invoice_line", "invoice",
            "order_item_cancellation", "order_item", "medical_order",
            "eligibility", "visit",
        ).forEach { jdbcTemplate.update("delete from $it") }
    }

    private val domainEventNames = setOf(
        "external.call", "visit.registered", "visit.duplicateRejected", "order.placed", "orderItem.cancelled",
        "invoice.calculated", "payment.started", "payment.cashReceived", "payment.completed",
        "payment.completionBlocked",
    )

    @Test
    fun `한 세션의 접수부터 수납 완료까지가 순서대로 이벤트 줄로 남고 같은 sessionId로 묶인다`(output: CapturedOutput) {
        val session = "S-flow-" + UUID.randomUUID().toString().take(8)
        val patientNo = "IT-" + UUID.randomUUID().toString().take(12)

        val visitId = id(post(session, "/api/v1/visits", registerBody(patientNo)), "$.visitId")
        post(
            session, "/api/v1/visits/$visitId/orders",
            """{"doctorNo":"D0001","orderItems":[
                {"feeItemId":${feeItemId("EX-CBC")},"quantity":2},
                {"feeItemId":${feeItemId("EX-NC-CERT")},"quantity":1}
            ]}""",
        )
        val invoiceId = id(post(session, "/api/v1/visits/$visitId/invoices"), "$.invoiceId")
        val counterId = counterId("C01")
        val paymentId = id(post(session, "/api/v1/invoices/$invoiceId/cash-payments", cash(counterId, 10000, 10000)), "$.paymentId")
        post(session, "/api/v1/invoices/$invoiceId/cash-payments", cash(counterId, 20000, 15000))

        val events = sessionLines(output, session).filter { it.read<String>("$.message") in domainEventNames }

        assertThat(events.map { it.read<String>("$.message") }).containsExactly(
            "external.call", "visit.registered", "order.placed", "invoice.calculated",
            "payment.started", "payment.cashReceived", "payment.cashReceived", "payment.completed",
        )

        val byName = events.associateBy { it.read<String>("$.message") }
        assertThat(byName.getValue("visit.registered").read<String>("$.visitId").toLong()).isEqualTo(visitId)
        assertThat(byName.getValue("visit.registered").read<String>("$.patientRef")).matches("^[0-9a-f]{8}$")
        assertThat(byName.getValue("visit.registered").read<String>("$.insuranceType")).isEqualTo("NATIONAL_HEALTH")
        assertThat(byName.getValue("external.call").read<String>("$.target")).isEqualTo("eligibility")
        assertThat(byName.getValue("external.call").read<String>("$.outcome")).isEqualTo("OK")

        assertThat(byName.getValue("order.placed").read<Number>("$.itemCount").toInt()).isEqualTo(2)
        assertThat(byName.getValue("invoice.calculated").read<String>("$.invoiceId").toLong()).isEqualTo(invoiceId)
        assertThat(byName.getValue("invoice.calculated").read<Number>("$.patientAmount").toLong()).isEqualTo(25000)

        assertThat(byName.getValue("payment.started").read<String>("$.paymentId").toLong()).isEqualTo(paymentId)
        val received = events.filter { it.read<String>("$.message") == "payment.cashReceived" }
        assertThat(received.map { it.read<Number>("$.remaining").toLong() }).containsExactly(15000L, 0L)
        assertThat(byName.getValue("payment.completed").read<Number>("$.itemCount").toInt()).isEqualTo(2)

        sessionLines(output, session).forEach { line ->
            val fields = line.read<Map<String, Any?>>("$")
            BusinessKeys.KEYS.forEach { key ->
                val value = fields[key]
                if (value != null) assertThat(value).describedAs("$key 타입").isInstanceOf(String::class.java)
            }
        }
    }

    @Test
    fun `이벤트 줄의 키는 MDC와 겹쳐 중복 필드가 생기지 않고 환자 원문이 로그에 없다`(output: CapturedOutput) {
        val session = "S-dup-" + UUID.randomUUID().toString().take(8)
        val patientNo = "IT-" + UUID.randomUUID().toString().take(12)

        val visitId = id(post(session, "/api/v1/visits", registerBody(patientNo)), "$.visitId")
        post(
            session, "/api/v1/visits/$visitId/orders",
            """{"doctorNo":"D0001","orderItems":[{"feeItemId":${feeItemId("EX-CBC")},"quantity":1}]}""",
        )
        post(session, "/api/v1/visits/$visitId/invoices")

        val rawLines = output.out.lineSequence().filter { it.startsWith("{") && it.contains(session) }.toList()
        val invoiceEvent = rawLines.single { it.contains("\"message\":\"invoice.calculated\"") }
        assertThat(invoiceEvent.split("\"visitId\"").size - 1).describedAs("visitId 필드 수").isEqualTo(1)

        assertThat(output.out).doesNotContain(patientNo)
    }

    @Test
    fun `같은 접수를 다시 보내면 등록 이벤트는 한 번만 남고 거부 이벤트에 환자 참조가 남는다`(output: CapturedOutput) {
        val session = "S-rej-" + UUID.randomUUID().toString().take(8)
        val patientNo = "IT-" + UUID.randomUUID().toString().take(12)

        post(session, "/api/v1/visits", registerBody(patientNo))
        post(session, "/api/v1/visits", registerBody(patientNo), expectedStatus = 409)

        val names = sessionLines(output, session).map { it.read<String>("$.message") }
        assertThat(names.count { it == "visit.registered" }).isEqualTo(1)
        assertThat(names.count { it == "visit.duplicateRejected" }).isEqualTo(1)

        val registered = sessionLines(output, session).single { it.read<String>("$.message") == "visit.registered" }
        val rejected = sessionLines(output, session).single { it.read<String>("$.message") == "visit.duplicateRejected" }
        assertThat(rejected.read<String>("$.patientRef")).isEqualTo(registered.read<String>("$.patientRef"))
    }

    @Test
    fun `산정 뒤 항목이 취소되면 취소 이벤트와 완료 차단 이벤트가 배열 필드와 함께 남는다`(output: CapturedOutput) {
        val session = "S-blk-" + UUID.randomUUID().toString().take(8)

        val visitId = id(post(session, "/api/v1/visits", registerBody("IT-" + UUID.randomUUID().toString().take(12))), "$.visitId")
        post(
            session, "/api/v1/visits/$visitId/orders",
            """{"doctorNo":"D0001","orderItems":[
                {"feeItemId":${feeItemId("EX-CBC")},"quantity":2},
                {"feeItemId":${feeItemId("EX-NC-CERT")},"quantity":1}
            ]}""",
        )
        val invoiceId = id(post(session, "/api/v1/visits/$visitId/invoices"), "$.invoiceId")

        val items = mockMvc.get("/api/v1/visits/$visitId/order-items").andReturn().response.contentAsString
        val orderItemId = JsonPath.read<Number>(items, "$.items[0].id").toLong()
        post(
            session, "/api/v1/order-items/$orderItemId/cancellation",
            """{"doctorNo":"D0001","reason":"환자 요청"}""",
        )
        post(session, "/api/v1/invoices/$invoiceId/cash-payments", cash(counterId("C01"), 25000, 25000), expectedStatus = 409)

        val lines = sessionLines(output, session)
        val cancelled = lines.single { it.read<String>("$.message") == "orderItem.cancelled" }
        assertThat(cancelled.read<String>("$.orderItemId").toLong()).isEqualTo(orderItemId)
        assertThat(cancelled.read<String>("$.from")).isEqualTo("ORDERED")
        assertThat(cancelled.read<String>("$.to")).isEqualTo("CANCELLED")
        assertThat(output.out).doesNotContain("환자 요청")

        val blocked = lines.single { it.read<String>("$.message") == "payment.completionBlocked" }
        assertThat(blocked.read<List<Number>>("$.ineffectiveItemIds").map { it.toLong() }).containsExactly(orderItemId)
        assertThat(blocked.read<List<Number>>("$.alreadyPaidItemIds")).isEmpty()
    }

    private fun sessionLines(output: CapturedOutput, session: String): List<DocumentContext> =
        output.out.lineSequence()
            .filter { it.startsWith("{") && it.contains(session) }
            .map { JsonPath.parse(it) }
            .toList()

    private fun post(session: String, path: String, body: String? = null, expectedStatus: Int? = null): MvcResult {
        val result = mockMvc.post(path) {
            header("X-Session-Id", session)
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }.andReturn()
        if (expectedStatus != null) assertThat(result.response.status).isEqualTo(expectedStatus)
        return result
    }

    private fun id(result: MvcResult, path: String): Long =
        JsonPath.read<Number>(result.response.contentAsString, path).toLong()

    private fun registerBody(patientNo: String) =
        """{"patientNo":"$patientNo","departmentCode":"IM","doctorNo":"D0001","visitDate":"2026-11-19"}"""

    private fun cash(counterId: Long, received: Long, applied: Long) =
        """{"counterId":$counterId,"cashierNo":"C0001","receivedAmount":$received,"appliedAmount":$applied}"""

    private fun feeItemId(code: String): Long =
        jdbcTemplate.queryForObject("select id from fee_item where code = ?", Long::class.javaObjectType, code)!!

    private fun counterId(code: String): Long =
        jdbcTemplate.queryForObject("select id from counter where code = ?", Long::class.javaObjectType, code)!!
}
