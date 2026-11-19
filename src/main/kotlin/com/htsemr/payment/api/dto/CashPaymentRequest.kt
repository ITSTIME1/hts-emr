package com.htsemr.payment.api.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class CashPaymentRequest(
    val counterId: Long,

    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 20, message = "최대 20자입니다")
    val cashierNo: String,

    @field:Min(value = 1, message = "1원 이상이어야 합니다")
    val receivedAmount: Long,

    @field:Min(value = 1, message = "1원 이상이어야 합니다")
    val appliedAmount: Long,
) {
    @get:AssertTrue(message = "받은 금액은 반영 금액 이상이어야 합니다")
    val receivedCoversApplied: Boolean
        get() = receivedAmount >= appliedAmount
}
