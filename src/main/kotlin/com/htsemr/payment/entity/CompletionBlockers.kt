package com.htsemr.payment.entity

internal data class CompletionBlockers(
    val ineffectiveItemIds: Set<Long>,
    val alreadyPaidItemIds: Set<Long>,
) {
    val isEmpty: Boolean get() = ineffectiveItemIds.isEmpty() && alreadyPaidItemIds.isEmpty()
}
