package com.htsemr.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.htsemr.observability.BusinessKeys
import org.slf4j.MDC
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

internal class BusinessKeyInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        variables?.forEach { (name, value) ->
            if (name in BusinessKeys.KEYS && value is String && NUMERIC_ID.matches(value)) {
                MDC.put(name as String, value)
            }
        }
        return true
    }

    companion object {
        private val NUMERIC_ID = Regex("^[0-9]{1,18}$")
    }
}
