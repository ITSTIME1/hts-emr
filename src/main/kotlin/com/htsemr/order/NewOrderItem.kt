package com.htsemr.order

data class NewOrderItem(
    val feeItemId: Long,
    val quantity: Int,
    val approvedByDoctor: Boolean,
)
