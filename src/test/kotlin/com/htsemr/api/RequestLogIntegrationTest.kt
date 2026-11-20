package com.htsemr.api

import com.htsemr.TestcontainersConfiguration
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
internal class RequestLogIntegrationTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val requestLogFilter: RequestLogFilter,
) {

    @Test
    fun `콘솔 JSON 로그에서 요청 줄의 필드와 requestId, sessionId가 최상위 필드로 나온다`(output: CapturedOutput) {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(requestLogFilter)
            .build()

        mockMvc.get("/api/v1/visits/999999") {
            header("X-Request-Id", "req-it-1")
            header("X-Session-Id", "S-it-1")
        }.andExpect { status { isNotFound() } }

        val line = output.out.lineSequence()
            .firstOrNull { it.contains("\"message\":\"http.request\"") && it.contains("req-it-1") }
        assertThat(line).describedAs("http.request JSON 줄").isNotNull()

        val json = JsonPath.parse(line!!)
        assertThat(json.read<String>("$.requestId")).isEqualTo("req-it-1")
        assertThat(json.read<String>("$.sessionId")).isEqualTo("S-it-1")
        assertThat(json.read<String>("$.method")).isEqualTo("GET")
        assertThat(json.read<String>("$.route")).isEqualTo("/api/v1/visits/{visitId}")
        assertThat(json.read<Int>("$.status")).isEqualTo(404)
        assertThat(json.read<String>("$.outcome")).isEqualTo("REJECTED")
        assertThat(json.read<String>("$.errorCode")).isEqualTo("VISIT_NOT_FOUND")
        assertThat(json.read<String>("$.visitId")).isEqualTo("999999")
        assertThat(json.read<Number>("$.durationMs").toLong()).isGreaterThanOrEqualTo(0)
        assertThat(json.read<String>("$.log.level")).isEqualTo("INFO")
    }

    @Test
    fun `존재하지 않는 경로는 route가 UNMATCHED이고 errorCode가 NOT_FOUND로 남는다`(output: CapturedOutput) {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(requestLogFilter)
            .build()

        mockMvc.get("/no/such/path/12345") {
            header("X-Request-Id", "req-it-404")
        }.andExpect { status { isNotFound() } }

        val line = output.out.lineSequence()
            .firstOrNull { it.contains("\"message\":\"http.request\"") && it.contains("req-it-404") }
        assertThat(line).describedAs("http.request JSON 줄").isNotNull()

        val json = JsonPath.parse(line!!)
        assertThat(json.read<String>("$.route")).isEqualTo("UNMATCHED")
        assertThat(json.read<Int>("$.status")).isEqualTo(404)
        assertThat(json.read<String>("$.errorCode")).isEqualTo("NOT_FOUND")
    }
}
