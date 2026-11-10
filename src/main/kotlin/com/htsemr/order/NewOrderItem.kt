package com.htsemr.order

/** 오더 발행 때 받는 항목 하나. */
data class NewOrderItem(
    val feeItemId: Long,
    val quantity: Int,
    val approvedByDoctor: Boolean,   // 의사의 선시행 지정
)