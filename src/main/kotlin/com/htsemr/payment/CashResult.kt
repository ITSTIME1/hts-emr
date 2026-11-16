package com.htsemr.payment

import com.htsemr.common.Money

sealed interface CashResult {
    val paymentId: Long

    data class InProgress(override val paymentId: Long, val remaining: Money) : CashResult

    data class Completed(override val paymentId: Long) : CashResult

    data class CompletionBlocked(
        override val paymentId: Long,
        val ineffectiveItemIds: Set<Long>,
        val alreadyPaidItemIds: Set<Long>,
    ) : CashResult
}
