package com.htsemr.order.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class CancelOrderItemRequest(
    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 20, message = "최대 20자입니다")
    val doctorNo: String,

    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 200, message = "최대 200자입니다")
    val reason: String,
)
