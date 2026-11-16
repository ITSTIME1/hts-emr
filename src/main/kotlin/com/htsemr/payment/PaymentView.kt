package com.htsemr.payment

import com.htsemr.common.Money
import java.time.LocalDateTime

data class PaymentView(
    val id: Long,
    val invoiceId: Long,
    val visitId: Long,
    val counterId: Long,
    val cashierNo: String,
    val patientAmount: Money,
    val paidAmount: Money,
    val status: PaymentStatus,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val orderItemIds: List<Long>,
    val lines: List<PaymentLineView>,
)

data class PaymentLineView(
    val id: Long,
    val method: PaymentMethod,
    val status: PaymentLineStatus,
    val receivedAmount: Money,
    val amount: Money,
    val change: Money,
    val receivedAt: LocalDateTime,
)
