package com.htsemr.order.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

internal data class PlaceOrderRequest(
    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 20, message = "최대 20자입니다")
    val doctorNo: String,

    @field:NotEmpty(message = "오더 항목이 하나 이상 필요합니다")
    @field:Valid
    val orderItems: List<OrderItemRequest>,
)

internal data class OrderItemRequest(
    val feeItemId: Long,

    @field:Min(value = 1, message = "1 이상이어야 합니다")
    val quantity: Int,

    val approvedByDoctor: Boolean = false,
)