package com.htsemr.billing

data class BillingTarget(
    val orderItemId: Long,
    val feeItemId: Long,
    val quantity: Int,
)
