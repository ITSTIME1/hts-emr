package com.htsemr.billing

/**
 * 산정 대상 항목 하나. 무엇을 산정할지는 payment가 고른다(A-02).
 *
 * 수가 항목과 수량을 함께 받는 이유: payment가 이미 오더 항목 조회 결과로 갖고 있는 값이라,
 * billing이 order에 다시 묻지 않아도 된다.
 */
data class BillingTarget(
    val orderItemId: Long,
    val feeItemId: Long,
    val quantity: Int,
)
