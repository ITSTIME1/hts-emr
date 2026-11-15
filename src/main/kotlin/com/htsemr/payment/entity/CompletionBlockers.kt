package com.htsemr.payment.entity

/**
 * 수납 완료를 막은 항목들(Payment.complete의 결과). 이유마다 따로 담는다. 한 번에 둘 다 걸릴 수 있다.
 *
 * @property ineffectiveItemIds 취소·취소 요청·소멸되어 효력이 없는 항목(INV-04)
 * @property alreadyPaidItemIds 다른 완료된 수납 건에 이미 들어 있는 항목(INV-03)
 */
internal data class CompletionBlockers(
    val ineffectiveItemIds: Set<Long>,
    val alreadyPaidItemIds: Set<Long>,
) {
    val isEmpty: Boolean get() = ineffectiveItemIds.isEmpty() && alreadyPaidItemIds.isEmpty()
}
