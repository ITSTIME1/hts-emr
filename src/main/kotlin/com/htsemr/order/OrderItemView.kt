package com.htsemr.order

/**
 * 오더 항목. order 모듈 밖으로 내보내는 값 객체다.
 *
 * 내원 건 id(visitId)는 오더에 저장된 값을 함께 보여 주는 것이며, 오더 항목에 따로 저장하지 않는다.
 *
 * 선시행 승인의 근거(preExecutionBasis)는 넣지 않는다. 다른 모듈은 수납 전에 시행할 수 있는지만 알면 되고,
 * 근거는 order 안의 사정이다(PreExecutionBasis는 internal이라 공개 타입에 넣을 수도 없다).
 */
data class OrderItemView(
    val id: Long,
    val orderId: Long,
    val visitId: Long,
    val feeItemId: Long,
    val quantity: Int,
    val status: OrderItemStatus,
    val executableBeforePayment: Boolean,
)
