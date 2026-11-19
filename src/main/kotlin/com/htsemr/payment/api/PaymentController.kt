package com.htsemr.payment.api

import com.htsemr.common.Money
import com.htsemr.payment.CashResult
import com.htsemr.payment.PaymentQueryService
import com.htsemr.payment.PaymentService
import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.api.dto.CashPaymentRequest
import com.htsemr.payment.api.dto.CashPaymentResponse
import com.htsemr.payment.api.dto.PaymentResponse
import com.htsemr.payment.api.dto.RequestedInvoice
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1")
internal class PaymentController(
    private val paymentService: PaymentService,
    private val paymentQueryService: PaymentQueryService,
) {

    @PostMapping("/visits/{visitId}/invoices")
    fun requestInvoice(@PathVariable visitId: Long): ResponseEntity<RequestedInvoice> {
        val invoiceId = paymentService.requestInvoice(visitId)

        return ResponseEntity
            .created(URI.create("/api/v1/invoices/$invoiceId"))
            .body(RequestedInvoice(invoiceId))
    }

    @PostMapping("/invoices/{invoiceId}/cash-payments")
    fun receiveCash(
        @PathVariable invoiceId: Long,
        @Valid @RequestBody request: CashPaymentRequest,
    ): ResponseEntity<Any> {
        val result = paymentService.receiveCash(
            invoiceId = invoiceId,
            counterId = request.counterId,
            cashierNo = request.cashierNo,
            received = Money(request.receivedAmount),
            amount = Money(request.appliedAmount),
        )

        return when (result) {
            is CashResult.InProgress -> ResponseEntity.ok(
                CashPaymentResponse(result.paymentId, PaymentStatus.IN_PROGRESS, result.remaining.amount),
            )

            is CashResult.Completed -> ResponseEntity.ok(
                CashPaymentResponse(result.paymentId, PaymentStatus.COMPLETED, null),
            )

            is CashResult.CompletionBlocked -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "결제 금액은 모두 받았지만 항목 문제로 수납을 완료하지 못했습니다",
                ).apply {
                    title = "Payment completion blocked"
                    setProperty("code", "PAYMENT_COMPLETION_BLOCKED")
                    setProperty("paymentId", result.paymentId)
                    setProperty("ineffectiveItemIds", result.ineffectiveItemIds.sorted())
                    setProperty("alreadyPaidItemIds", result.alreadyPaidItemIds.sorted())
                },
            )
        }
    }

    @GetMapping("/payments/{paymentId}")
    fun getPayment(@PathVariable paymentId: Long): PaymentResponse =
        PaymentResponse.from(paymentQueryService.payment(paymentId))
}
