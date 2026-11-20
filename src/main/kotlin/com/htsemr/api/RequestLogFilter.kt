package com.htsemr.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class RequestLogFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith(ACTUATOR_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        val requestId = safeHeader(request, REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()
        val sessionId = safeHeader(request, SESSION_ID_HEADER)

        MDC.put(REQUEST_ID_KEY, requestId)
        if (sessionId != null) MDC.put(SESSION_ID_KEY, sessionId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        var failure: Throwable? = null
        try {
            filterChain.doFilter(request, response)
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            val status = if (failure != null) HttpServletResponse.SC_INTERNAL_SERVER_ERROR else response.status
            val route = (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String)
                ?.takeUnless { it == STATIC_RESOURCE_PATTERN }
                ?: UNMATCHED

            val errorCode = if (failure != null) {
                ErrorCodes.INTERNAL_ERROR
            } else {
                request.getAttribute(ERROR_CODE_ATTRIBUTE) as? String
            }

            val event = log.atInfo()
                .addKeyValue("method", request.method)
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
                .addKeyValue("outcome", outcomeOf(status))
            (if (errorCode != null) event.addKeyValue("errorCode", errorCode) else event).log(MESSAGE)

            MDC.remove(REQUEST_ID_KEY)
            MDC.remove(SESSION_ID_KEY)
            BusinessKeyInterceptor.KEYS.forEach { MDC.remove(it) }
        }
    }

    private fun safeHeader(request: HttpServletRequest, name: String): String? =
        request.getHeader(name)?.takeIf { SAFE_ID.matches(it) }

    private fun outcomeOf(status: Int): String = when {
        status < 400 -> "OK"
        status < 500 -> "REJECTED"
        else -> "FAILED"
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val SESSION_ID_HEADER = "X-Session-Id"
        const val MESSAGE = "http.request"
        const val UNMATCHED = "UNMATCHED"
        const val ERROR_CODE_ATTRIBUTE = "com.htsemr.api.errorCode"

        private const val REQUEST_ID_KEY = "requestId"
        private const val SESSION_ID_KEY = "sessionId"
        private const val STATIC_RESOURCE_PATTERN = "/**"
        private const val ACTUATOR_PREFIX = "/actuator"
        private const val NANOS_PER_MILLI = 1_000_000L

        private val SAFE_ID = Regex("^[A-Za-z0-9._-]{1,64}$")
        private val log = LoggerFactory.getLogger(RequestLogFilter::class.java)
    }
}
