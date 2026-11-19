package com.htsemr.payment.api.dto

import com.htsemr.payment.PaymentStatus

internal data class CashPaymentResponse(
    val paymentId: Long,
    val status: PaymentStatus,
    val remaining: Long?,
)
