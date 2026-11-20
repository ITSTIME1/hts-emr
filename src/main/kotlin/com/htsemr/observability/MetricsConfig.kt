package com.htsemr.observability

import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class MetricsConfig {

    @Bean
    fun excludeActuatorRequests(): MeterFilter = MeterFilter.deny { id ->
        id.name.startsWith(HTTP_SERVER_REQUESTS) && id.getTag(URI_TAG)?.startsWith(ACTUATOR_PREFIX) == true
    }

    private companion object {
        const val HTTP_SERVER_REQUESTS = "http.server.requests"
        const val URI_TAG = "uri"
        const val ACTUATOR_PREFIX = "/actuator"
    }
}
