package com.htsemr.api

import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.master.CounterNotFoundException
import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.master.FeePriceNotFoundException
import com.htsemr.order.NoPriceOnVisitDateException
import com.htsemr.order.OrderItemNotCancellableException
import com.htsemr.order.OrderItemNotFoundException
import com.htsemr.payment.InvoiceAlreadyUsedException
import com.htsemr.payment.NothingToPayException
import com.htsemr.payment.PaymentAmountExceededException
import com.htsemr.payment.PaymentCompletionConflictException
import com.htsemr.payment.PaymentNotFoundException
import com.htsemr.payment.PaymentStartConflictException
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

    @ExceptionHandler(FeeItemNotFoundException::class)
    fun feeItemNotFound(ex: FeeItemNotFoundException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "해당 수가 항목을 찾을 수 없습니다",
        )
        problem.title = "Fee item not found"
        problem.setProperty("code", "FEE_ITEM_NOT_FOUND")
        return problem
    }

    @ExceptionHandler(OrderItemNotFoundException::class)
    fun orderItemNotFound(ex: OrderItemNotFoundException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "해당 오더 항목을 찾을 수 없습니다",
        )
        problem.title = "Order item not found"
        problem.setProperty("code", "ORDER_ITEM_NOT_FOUND")
        return problem
    }

    @ExceptionHandler(NoPriceOnVisitDateException::class)
    fun noPriceOnVisitDate(ex: NoPriceOnVisitDateException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "진료일에 유효한 수가가 없는 수가 항목입니다",
        )
        problem.title = "No price on visit date"
        problem.setProperty("code", "NO_PRICE_ON_VISIT_DATE")
        return problem
    }

    @ExceptionHandler(OrderItemNotCancellableException::class)
    fun orderItemNotCancellable(ex: OrderItemNotCancellableException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "현재 상태에서는 오더 항목을 취소할 수 없습니다",
        )
        problem.title = "Order item not cancellable"
        problem.setProperty("code", "ORDER_ITEM_NOT_CANCELLABLE")
        problem.setProperty("currentStatus", ex.status.name)
        return problem
    }

    @ExceptionHandler(InvoiceNotFoundException::class)
    fun invoiceNotFound(ex: InvoiceNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Invoice not found", "INVOICE_NOT_FOUND", "해당 산정을 찾을 수 없습니다")

    @ExceptionHandler(CounterNotFoundException::class)
    fun counterNotFound(ex: CounterNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Counter not found", "COUNTER_NOT_FOUND", "해당 창구를 찾을 수 없습니다")

    @ExceptionHandler(PaymentNotFoundException::class)
    fun paymentNotFound(ex: PaymentNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Payment not found", "PAYMENT_NOT_FOUND", "해당 수납 건을 찾을 수 없습니다")

    @ExceptionHandler(NothingToPayException::class)
    fun nothingToPay(ex: NothingToPayException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Nothing to pay", "NOTHING_TO_PAY", "수납할 오더 항목이 없습니다")

    @ExceptionHandler(InvoiceAlreadyUsedException::class)
    fun invoiceAlreadyUsed(ex: InvoiceAlreadyUsedException): ProblemDetail =
        problem(
            HttpStatus.CONFLICT, "Invoice already used", "INVOICE_ALREADY_USED",
            "이 산정으로 만든 수납 건이 이미 있습니다. 다시 받으려면 새로 산정하세요",
        )

    @ExceptionHandler(PaymentStartConflictException::class)
    fun paymentStartConflict(ex: PaymentStartConflictException): ProblemDetail =
        problem(
            HttpStatus.CONFLICT, "Payment start conflict", "PAYMENT_START_CONFLICT",
            "다른 창구에서 방금 같은 산정으로 수납을 시작했습니다. 다시 시도하세요",
        )

    @ExceptionHandler(PaymentCompletionConflictException::class)
    fun paymentCompletionConflict(ex: PaymentCompletionConflictException): ProblemDetail =
        problem(
            HttpStatus.CONFLICT, "Payment completion conflict", "PAYMENT_COMPLETION_CONFLICT",
            "같은 항목을 다른 수납 건이 방금 완료했습니다. 이 결제는 저장되지 않았습니다",
        )

    @ExceptionHandler(PaymentAmountExceededException::class)
    fun paymentAmountExceeded(ex: PaymentAmountExceededException): ProblemDetail =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY, "Payment amount exceeded", "PAYMENT_AMOUNT_EXCEEDED",
            "결제 합계가 본인부담 합계를 넘습니다",
        )

    @ExceptionHandler(FeePriceNotFoundException::class)
    fun feePriceNotFound(ex: FeePriceNotFoundException): ProblemDetail =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY, "Fee price not found", "FEE_PRICE_NOT_FOUND",
            "진료일에 유효한 단가가 없는 수가 항목이 있습니다",
        )

    private fun problem(status: HttpStatus, title: String, code: String, detail: String): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.title = title
        problem.setProperty("code", code)
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
