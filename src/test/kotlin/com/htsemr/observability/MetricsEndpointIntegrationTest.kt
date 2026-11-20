package com.htsemr.observability

import com.htsemr.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@Import(TestcontainersConfiguration::class)
internal class MetricsEndpointIntegrationTest @Autowired constructor(
    private val environment: Environment,
) {

    private val client = HttpClient.newHttpClient()

    private fun get(path: String): HttpResponse<String> {
        val port = environment.getRequiredProperty("local.server.port")
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun scrape(): String = get("/actuator/prometheus").body()

    private fun requestLines(metrics: String): List<String> =
        metrics.lines().filter { it.startsWith("http_server_requests_seconds") }

    @Test
    fun `prometheus 엔드포인트가 텍스트 형식으로 지표를 내놓는다`() {
        val response = get("/actuator/prometheus")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/plain")
        assertThat(response.body()).contains("jvm_memory_used_bytes").contains("hikaricp_connections")
    }

    @Test
    fun `웹으로 노출하는 엔드포인트는 health와 prometheus뿐이다`() {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200)
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200)

        listOf("env", "beans", "metrics", "info", "configprops", "mappings", "loggers").forEach {
            assertThat(get("/actuator/$it").statusCode()).describedAs("/actuator/$it").isEqualTo(404)
        }
    }

    @Test
    fun `모든 지표에 color와 version 꼬리표가 붙는다`() {
        get("/api/v1/visits/999999")

        val lines = requestLines(scrape())
        assertThat(lines).isNotEmpty()
        assertThat(lines).allMatch { it.contains("color=\"local\"") && it.contains("version=\"local\"") }
        assertThat(scrape().lines().filter { it.startsWith("jvm_memory_used_bytes") })
            .allMatch { it.contains("color=\"local\"") && it.contains("version=\"local\"") }
    }

    @Test
    fun `요청 지연이 히스토그램 버킷으로 나온다`() {
        get("/api/v1/visits/999999")

        val buckets = requestLines(scrape()).filter { it.startsWith("http_server_requests_seconds_bucket") }
        assertThat(buckets).isNotEmpty()
        assertThat(buckets).anyMatch { it.contains("le=\"+Inf\"") }
        assertThat(buckets.map { Regex("le=\"([^\"]+)\"").find(it)!!.groupValues[1] }.distinct().size).isGreaterThan(5)
    }

    @Test
    fun `uri 꼬리표는 템플릿이고 실제 경로 값은 나오지 않는다`() {
        get("/api/v1/visits/999999")
        get("/no/such/path/777777")

        val metrics = scrape()
        val uris = requestLines(metrics)
            .mapNotNull { Regex("uri=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            .toSet()
        assertThat(uris).contains("/api/v1/visits/{visitId}")
        assertThat(uris).noneMatch { it.contains("999999") || it.contains("777777") }

        val notFoundUris = requestLines(metrics)
            .filter { it.contains("status=\"404\"") }
            .map { Regex("uri=\"([^\"]+)\"").find(it)!!.groupValues[1] }
            .toSet()
        assertThat(notFoundUris).containsExactlyInAnyOrder("/api/v1/visits/{visitId}", "/**")
    }

    @Test
    fun `actuator 요청은 http 지표에 집계되지 않는다`() {
        get("/actuator/health")
        get("/actuator/health/readiness")
        scrape()

        assertThat(requestLines(scrape())).noneMatch { it.contains("uri=\"/actuator") }
    }
}
