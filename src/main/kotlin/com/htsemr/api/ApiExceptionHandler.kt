package com.htsemr.api

import com.htsemr.reception.DuplicateVisitException
import com.htsemr.reception.VisitNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
internal class ApiExceptionHandler : ResponseEntityExceptionHandler() {


    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val problem = response?.body as? ProblemDetail
        if (problem != null && problem.properties?.containsKey("code") != true) {
            problem.setProperty("code", HttpStatus.valueOf(statusCode.value()).name)
        }
        return response
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다")
        problem.title = "Validation failed"
        problem.setProperty("code", "VALIDATION_FAILED")
        problem.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "reason" to it.defaultMessage) },
        )
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문을 읽을 수 없습니다. JSON 형식과 필드 값의 형식을 확인하세요",
        )
        problem.title = "Malformed request"
        problem.setProperty("code", "MALFORMED_REQUEST")
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(DuplicateVisitException::class)
    fun duplicateVisit(ex: DuplicateVisitException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "같은 환자의 같은 진료일, 같은 진료과 접수가 이미 있습니다",
        )
        problem.title = "Duplicate visit"
        problem.setProperty("code", "DUPLICATE_VISIT")
        return problem
    }

    @ExceptionHandler(VisitNotFoundException::class)
    fun visitNotFound(ex: VisitNotFoundException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "해당 접수를 찾을 수 없습니다",
        )
        problem.title = "Visit not found"
        problem.setProperty("code", "VISIT_NOT_FOUND")
        return problem
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception): ProblemDetail {
        log.error("처리하지 못한 예외", ex)

        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버에서 요청을 처리하지 못했습니다",
        )
        problem.title = "Internal error"
        problem.setProperty("code", "INTERNAL_ERROR")
        return problem
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
