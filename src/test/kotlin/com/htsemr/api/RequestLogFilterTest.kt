package com.htsemr.api

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.htsemr.common.Money
import com.htsemr.payment.CashResult
import com.htsemr.payment.NothingToPayException
import com.htsemr.payment.PaymentAmountExceededException
import com.htsemr.payment.PaymentQueryService
import com.htsemr.payment.PaymentService
import com.htsemr.payment.api.PaymentController
import com.htsemr.reception.VisitNotFoundException
import com.jayway.jsonpath.JsonPath
import jakarta.servlet.ServletException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

internal class RequestLogFilterTest {

    private val requestLogAppender = ListAppender<ILoggingEvent>()
    private val requestLogLogger = LoggerFactory.getLogger(RequestLogFilter::class.java) as Logger

    private val handlerAppender = ListAppender<ILoggingEvent>()
    private val handlerLogger = LoggerFactory.getLogger(ApiExceptionHandler::class.java) as Logger

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(TestController())
        .setControllerAdvice(ApiExceptionHandler(), ErrorCodeAdvice())
        .addFilters<StandaloneMockMvcBuilder>(RequestLogFilter())
        .build()

    private val paymentService = mock(PaymentService::class.java)

    private val paymentMockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(PaymentController(paymentService, mock(PaymentQueryService::class.java)))
        .setControllerAdvice(ApiExceptionHandler(), ErrorCodeAdvice())
        .addFilters<StandaloneMockMvcBuilder>(RequestLogFilter())
        .build()

    private val mockMvcWithoutAdvice: MockMvc = MockMvcBuilders
        .standaloneSetup(TestController())
        .addFilters<StandaloneMockMvcBuilder>(RequestLogFilter())
        .build()

    @BeforeEach
    fun attachAppenders() {
        MDC.clear()
        requestLogLogger.level = Level.INFO
        requestLogAppender.start()
        requestLogLogger.addAppender(requestLogAppender)

        handlerAppender.start()
        handlerLogger.addAppender(handlerAppender)
    }

    @AfterEach
    fun detachAppenders() {
        requestLogLogger.detachAppender(requestLogAppender)
        requestLogAppender.stop()
        handlerLogger.detachAppender(handlerAppender)
        handlerAppender.stop()
        MDC.clear()
    }

    private fun requestLines(): List<ILoggingEvent> =
        requestLogAppender.list.filter { it.formattedMessage == RequestLogFilter.MESSAGE }

    private fun ILoggingEvent.field(key: String): Any? =
        keyValuePairs?.firstOrNull { it.key == key }?.value

    private fun bodyCode(body: String): String = JsonPath.read(body, "$.code")

    @Test
    fun `정상 요청은 요청 줄을 한 줄 남기고 필드가 채워진다`() {
        mockMvc.get("/api/v1/visits/57") {
            header("X-Session-Id", "S-0412")
        }.andExpect { status { isOk() } }

        val lines = requestLines()
        assertThat(lines).hasSize(1)

        val line = lines.single()
        assertThat(line.field("method")).isEqualTo("GET")
        assertThat(line.field("route")).isEqualTo("/api/v1/visits/{visitId}")
        assertThat(line.field("status")).isEqualTo(200)
        assertThat(line.field("outcome")).isEqualTo("OK")
        assertThat((line.field("durationMs") as Number).toLong()).isGreaterThanOrEqualTo(0)
        assertThat(line.mdcPropertyMap["requestId"]).isNotBlank()
        assertThat(line.mdcPropertyMap["sessionId"]).isEqualTo("S-0412")
    }

    @Test
    fun `route는 실제 값이 아니라 템플릿으로 남는다`() {
        mockMvc.get("/api/v1/visits/57").andReturn()
        mockMvc.get("/api/v1/visits/58").andReturn()

        assertThat(requestLines().map { it.field("route") })
            .containsExactly("/api/v1/visits/{visitId}", "/api/v1/visits/{visitId}")
    }

    @Test
    fun `업무 거부로 끝난 요청도 한 줄이고 상태는 실제 응답 상태다`() {
        mockMvc.get("/api/v1/visits/57/conflict").andExpect { status { isConflict() } }

        val line = requestLines().single()
        assertThat(line.field("status")).isEqualTo(409)
        assertThat(line.field("outcome")).isEqualTo("REJECTED")
        assertThat(line.field("route")).isEqualTo("/api/v1/visits/{visitId}/conflict")
    }

    @Test
    fun `핸들러가 처리한 예상 밖 예외는 500 FAILED 요청 줄 한 줄이고 오류 로그에도 requestId가 실린다`() {
        mockMvc.get("/boom") {
            header("X-Request-Id", "req-500")
        }.andExpect { status { isInternalServerError() } }

        val line = requestLines().single()
        assertThat(line.field("status")).isEqualTo(500)
        assertThat(line.field("outcome")).isEqualTo("FAILED")
        assertThat(line.field("errorCode")).isEqualTo("INTERNAL_ERROR")

        val errorLog = handlerAppender.list.single { it.level == Level.ERROR }
        assertThat(errorLog.mdcPropertyMap["requestId"]).isEqualTo("req-500")
    }

    @Test
    fun `핸들러 밖으로 예외가 새도 500 FAILED로 남기고 예외는 그대로 던진다`() {
        assertThatThrownBy { mockMvcWithoutAdvice.get("/boom") }.isInstanceOf(ServletException::class.java)

        val line = requestLines().single()
        assertThat(line.field("status")).isEqualTo(500)
        assertThat(line.field("outcome")).isEqualTo("FAILED")
        assertThat(line.field("errorCode")).isEqualTo("INTERNAL_ERROR")
        assertThat(MDC.get("requestId")).isNull()
    }

    @Test
    fun `X-Request-Id가 안전하면 그대로 쓰고 응답 헤더로도 돌려준다`() {
        mockMvc.get("/api/v1/visits/57") {
            header("X-Request-Id", "req-abc.123_X-9")
        }.andExpect {
            header { string("X-Request-Id", "req-abc.123_X-9") }
        }

        assertThat(requestLines().single().mdcPropertyMap["requestId"]).isEqualTo("req-abc.123_X-9")
    }

    @Test
    fun `X-Request-Id가 없으면 새로 만들고 응답 헤더와 로그의 값이 같다`() {
        val responseHeader = mockMvc.get("/api/v1/visits/57").andReturn().response.getHeader("X-Request-Id")

        assertThat(responseHeader).isNotBlank()
        assertThat(requestLines().single().mdcPropertyMap["requestId"]).isEqualTo(responseHeader)
    }

    @Test
    fun `안전하지 않은 X-Request-Id는 버리고 새로 만든다`() {
        val tooLong = "a".repeat(65)
        val withSpace = "bad id"

        listOf(tooLong, withSpace).forEach { unsafe ->
            val responseHeader = mockMvc.get("/api/v1/visits/57") {
                header("X-Request-Id", unsafe)
            }.andReturn().response.getHeader("X-Request-Id")

            assertThat(responseHeader).isNotEqualTo(unsafe)
            assertThat(responseHeader).matches("^[A-Za-z0-9._-]{1,64}$")
        }
    }

    @Test
    fun `X-Session-Id가 없거나 안전하지 않으면 sessionId 필드를 생략한다`() {
        mockMvc.get("/api/v1/visits/57").andReturn()
        mockMvc.get("/api/v1/visits/57") { header("X-Session-Id", "bad session") }.andReturn()

        requestLines().forEach { assertThat(it.mdcPropertyMap).doesNotContainKey("sessionId") }
    }

    @Test
    fun `이전 요청의 sessionId가 다음 요청 로그로 새지 않고 요청이 끝나면 MDC가 비워진다`() {
        mockMvc.get("/api/v1/visits/57") { header("X-Session-Id", "S-1") }.andReturn()
        mockMvc.get("/api/v1/visits/58").andReturn()

        val (first, second) = requestLines()
        assertThat(first.mdcPropertyMap["sessionId"]).isEqualTo("S-1")
        assertThat(second.mdcPropertyMap).doesNotContainKey("sessionId")
        assertThat(MDC.get("requestId")).isNull()
        assertThat(MDC.get("sessionId")).isNull()
    }

    @Test
    fun `actuator 요청은 요청 줄을 남기지 않는다`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }

        assertThat(requestLines()).isEmpty()
    }

    @Test
    fun `매핑되는 핸들러가 없는 경로는 UNMATCHED로 남고 원본 경로가 새지 않는다`() {
        mockMvc.get("/no/such/path/12345").andExpect { status { isNotFound() } }

        val line = requestLines().single()
        assertThat(line.field("route")).isEqualTo("UNMATCHED")
        assertThat(line.field("status")).isEqualTo(404)
        assertThat(line.field("outcome")).isEqualTo("REJECTED")
    }

    @Test
    fun `정상 요청에는 errorCode 필드가 없다`() {
        mockMvc.get("/api/v1/visits/57").andReturn()

        assertThat(requestLines().single().keyValuePairs.map { it.key }).doesNotContain("errorCode")
    }

    @Test
    fun `업무 예외로 끝난 요청의 errorCode는 응답 본문의 code와 같다`() {
        listOf(
            "/api/v1/visits/57/conflict" to "NOTHING_TO_PAY",
            "/api/v1/visits/57/missing" to "VISIT_NOT_FOUND",
            "/api/v1/visits/57/exceeded" to "PAYMENT_AMOUNT_EXCEEDED",
        ).forEach { (path, expectedCode) ->
            val body = mockMvc.get(path).andReturn().response.contentAsString

            assertThat(bodyCode(body)).isEqualTo(expectedCode)
            assertThat(requestLines().last().field("errorCode")).isEqualTo(bodyCode(body))
        }
    }

    @Test
    fun `검증 실패와 깨진 본문과 허용되지 않는 메서드도 본문의 code가 errorCode로 남는다`() {
        val validation = mockMvc.post("/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":""}"""
        }.andReturn().response.contentAsString
        assertThat(requestLines().last().field("errorCode")).isEqualTo("VALIDATION_FAILED").isEqualTo(bodyCode(validation))

        val malformed = mockMvc.post("/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = "{broken"
        }.andReturn().response.contentAsString
        assertThat(requestLines().last().field("errorCode")).isEqualTo("MALFORMED_REQUEST").isEqualTo(bodyCode(malformed))

        val notAllowed = mockMvc.get("/validate").andReturn().response.contentAsString
        assertThat(requestLines().last().field("errorCode")).isEqualTo("METHOD_NOT_ALLOWED").isEqualTo(bodyCode(notAllowed))
    }

    @Test
    fun `컨트롤러가 예외 없이 직접 돌려준 에러 응답의 code도 errorCode로 남는다`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willReturn(CashResult.CompletionBlocked(800L, setOf(901L), setOf(903L)))

        val body = paymentMockMvc.post("/api/v1/invoices/700/cash-payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"counterId":3,"cashierNo":"C0001","receivedAmount":10000,"appliedAmount":5000}"""
        }.andExpect { status { isConflict() } }.andReturn().response.contentAsString

        val line = requestLines().single()
        assertThat(bodyCode(body)).isEqualTo("PAYMENT_COMPLETION_BLOCKED")
        assertThat(line.field("errorCode")).isEqualTo("PAYMENT_COMPLETION_BLOCKED")
        assertThat(line.field("outcome")).isEqualTo("REJECTED")
    }

    @RestController
    private class TestController {

        @GetMapping("/api/v1/visits/{visitId}/missing")
        fun missing(@PathVariable visitId: Long): String = throw VisitNotFoundException(visitId)

        @GetMapping("/api/v1/visits/{visitId}/exceeded")
        fun exceeded(@PathVariable visitId: Long): String =
            throw PaymentAmountExceededException(visitId, Money(1000), Money(2000))

        @PostMapping("/validate")
        fun validate(@Valid @RequestBody request: ValidateRequest): String = "ok"

        data class ValidateRequest(@field:NotBlank val name: String)


        @GetMapping("/api/v1/visits/{visitId}")
        fun visit(@PathVariable visitId: Long): String = "ok"

        @GetMapping("/api/v1/visits/{visitId}/conflict")
        fun conflict(@PathVariable visitId: Long): String = throw NothingToPayException(visitId)

        @GetMapping("/boom")
        fun boom(): String = throw IllegalStateException("boom")

        @GetMapping("/actuator/health")
        fun health(): String = "UP"
    }
}
