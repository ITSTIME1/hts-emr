package com.htsemr.api

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.htsemr.observability.BusinessKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

internal class BusinessKeyMdcTest {

    private val requestLogAppender = ListAppender<ILoggingEvent>()
    private val requestLogLogger = LoggerFactory.getLogger(RequestLogFilter::class.java) as Logger

    private val handlerAppender = ListAppender<ILoggingEvent>()
    private val handlerLogger = LoggerFactory.getLogger(ApiExceptionHandler::class.java) as Logger

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(TestController())
        .setControllerAdvice(ApiExceptionHandler(), ErrorCodeAdvice())
        .addInterceptors(BusinessKeyInterceptor())
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

    private fun ILoggingEvent.businessKeys(): Map<String, String> =
        mdcPropertyMap.filterKeys { it in BusinessKeys.KEYS }

    private fun assertNoBusinessKeysInMdc() {
        BusinessKeys.KEYS.forEach { assertThat(MDC.get(it)).describedAs(it).isNull() }
    }

    @Test
    fun `경로 변수에 따라 맞는 업무 키만 요청 줄에 실린다`() {
        listOf(
            "/api/v1/visits/57" to mapOf("visitId" to "57"),
            "/api/v1/invoices/9/cash-payments" to mapOf("invoiceId" to "9"),
            "/api/v1/payments/3307" to mapOf("paymentId" to "3307"),
            "/api/v1/order-items/41/cancellation" to mapOf("orderItemId" to "41"),
        ).forEach { (path, expectedKeys) ->
            mockMvc.get(path).andReturn()

            assertThat(requestLines().last().businessKeys()).describedAs(path).isEqualTo(expectedKeys)
        }
    }

    @Test
    fun `같은 요청 중에 찍힌 오류 로그에도 업무 키가 실린다`() {
        mockMvc.get("/api/v1/visits/57/boom").andReturn()

        assertThat(requestLines().single().businessKeys()).isEqualTo(mapOf("visitId" to "57"))
        val errorLog = handlerAppender.list.single { it.level == Level.ERROR }
        assertThat(errorLog.businessKeys()).isEqualTo(mapOf("visitId" to "57"))
    }

    @Test
    fun `숫자가 아니거나 너무 긴 값은 키를 생략하고 원본 값이 로그에 남지 않는다`() {
        mockMvc.get("/api/v1/visits/abc") { header("X-Request-Id", "req-1") }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/v1/visits/${"1".repeat(19)}") { header("X-Request-Id", "req-2") }.andReturn()

        requestLines().forEach { line ->
            assertThat(line.businessKeys()).isEmpty()
            assertThat(line.mdcPropertyMap.values).noneMatch { it.contains("abc") || it.startsWith("1111") }
        }
        assertThat(requestLines()).hasSize(2)
    }

    @Test
    fun `이전 요청의 업무 키가 다음 요청 로그로 새지 않는다`() {
        mockMvc.get("/api/v1/visits/57").andReturn()
        mockMvc.get("/api/v1/payments/9").andReturn()

        val (first, second) = requestLines()
        assertThat(first.businessKeys()).isEqualTo(mapOf("visitId" to "57"))
        assertThat(second.businessKeys()).isEqualTo(mapOf("paymentId" to "9"))
        assertNoBusinessKeysInMdc()
    }

    @Test
    fun `예외로 끝난 요청 뒤에도 업무 키가 MDC에 남지 않는다`() {
        mockMvc.get("/api/v1/visits/57/boom").andReturn()

        assertNoBusinessKeysInMdc()
    }

    @Test
    fun `경로 변수가 없거나 매핑이 없거나 actuator인 요청에는 업무 키가 없다`() {
        mockMvc.post("/api/v1/visits").andReturn()
        mockMvc.get("/no/such/path/12345").andReturn()
        mockMvc.get("/actuator/health").andReturn()

        requestLines().forEach { assertThat(it.businessKeys()).isEmpty() }
        assertThat(requestLines()).hasSize(2)
        assertNoBusinessKeysInMdc()
    }

    @Test
    fun `허용 목록에 없는 경로 변수는 로그에 싣지 않는다`() {
        mockMvc.get("/api/v1/other/777").andReturn()

        val line = requestLines().single()
        assertThat(line.mdcPropertyMap.keys).doesNotContain("foo")
        assertThat(line.mdcPropertyMap.values).doesNotContain("777")
    }

    @RestController
    private class TestController {

        @GetMapping("/api/v1/visits/{visitId}")
        fun visit(@PathVariable visitId: Long): String = "ok"

        @GetMapping("/api/v1/visits/{visitId}/boom")
        fun boom(@PathVariable visitId: Long): String = throw IllegalStateException("boom")

        @GetMapping("/api/v1/invoices/{invoiceId}/cash-payments")
        fun invoice(@PathVariable invoiceId: Long): String = "ok"

        @GetMapping("/api/v1/payments/{paymentId}")
        fun payment(@PathVariable paymentId: Long): String = "ok"

        @GetMapping("/api/v1/order-items/{orderItemId}/cancellation")
        fun orderItem(@PathVariable orderItemId: Long): String = "ok"

        @GetMapping("/api/v1/other/{foo}")
        fun other(@PathVariable foo: String): String = "ok"

        @PostMapping("/api/v1/visits")
        fun register(): String = "ok"

        @GetMapping("/actuator/health")
        fun health(): String = "UP"
    }
}
