package com.htsemr.payment.api.dto

import com.htsemr.payment.PaymentLineStatus
import com.htsemr.payment.PaymentLineView
import com.htsemr.payment.PaymentMethod
import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.PaymentView
import java.time.LocalDateTime

internal data class PaymentResponse(
    val id: Long,
    val invoiceId: Long,
    val visitId: Long,
    val counterId: Long,
    val cashierNo: String,
    val patientAmount: Long,
    val paidAmount: Long,
    val status: PaymentStatus,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val orderItemIds: List<Long>,
    val lines: List<PaymentLineResponse>,
) {
    companion object {
        fun from(view: PaymentView) = PaymentResponse(
            id = view.id,
            invoiceId = view.invoiceId,
            visitId = view.visitId,
            counterId = view.counterId,
            cashierNo = view.cashierNo,
            patientAmount = view.patientAmount.amount,
            paidAmount = view.paidAmount.amount,
            status = view.status,
            startedAt = view.startedAt,
            completedAt = view.completedAt,
            orderItemIds = view.orderItemIds,
            lines = view.lines.map { PaymentLineResponse.from(it) },
        )
    }
}

internal data class PaymentLineResponse(
    val id: Long,
    val method: PaymentMethod,
    val status: PaymentLineStatus,
    val receivedAmount: Long,
    val appliedAmount: Long,
    val change: Long,
    val receivedAt: LocalDateTime,
) {
    companion object {
        fun from(view: PaymentLineView) = PaymentLineResponse(
            id = view.id,
            method = view.method,
            status = view.status,
            receivedAmount = view.receivedAmount.amount,
            appliedAmount = view.amount.amount,
            change = view.change.amount,
            receivedAt = view.receivedAt,
        )
    }
}
