package com.htsemr.order

data class OrderItemView(
    val id: Long,
    val orderId: Long,
    val visitId: Long,
    val feeItemId: Long,
    val quantity: Int,
    val status: OrderItemStatus,
    val executableBeforePayment: Boolean,
)
