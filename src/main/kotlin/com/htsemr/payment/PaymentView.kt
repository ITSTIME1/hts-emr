package com.htsemr.payment

import com.htsemr.common.Money
import java.time.LocalDateTime

/**
 * 수납 건. payment 모듈 밖으로 내보내는 값 객체다.
 *
 * paidAmount는 받은(RECEIVED) 결제의 수납 반영 금액 합이다. 저장하지 않고 결제에서 계산한다.
 */
data class PaymentView(
    val id: Long,
    val invoiceId: Long,
    val visitId: Long,
    val counterId: Long,
    val cashierNo: String,
    val patientAmount: Money,
    val paidAmount: Money,
    val status: PaymentStatus,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val orderItemIds: List<Long>,
    val lines: List<PaymentLineView>,
)

/** 결제. 거스름돈(change)은 저장하지 않고 받은 금액 − 수납 반영 금액으로 계산한다. */
data class PaymentLineView(
    val id: Long,
    val method: PaymentMethod,
    val status: PaymentLineStatus,
    val receivedAmount: Money,
    val appliedAmount: Money,
    val change: Money,
    val receivedAt: LocalDateTime,
)
