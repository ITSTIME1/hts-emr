package com.htsemr.payment

import com.htsemr.common.Money

/** 수납할 오더 항목이 없다. 효력 있는 항목이 없거나, 모두 완료된 수납 건에 들어 있다(A-02). */
class NothingToPayException(val visitId: Long)
    : RuntimeException("내원 건 $visitId 에 수납할 오더 항목이 없습니다")

/** 이 결제를 더하면 결제 합계가 본인부담 합계를 넘는다(A-20). */
class PaymentAmountExceededException(val invoiceId: Long, val patientAmount: Money, val paidAmountAfter: Money)
    : RuntimeException("산정 $invoiceId: 결제 합계 ${paidAmountAfter.amount}원이 본인부담 합계 ${patientAmount.amount}원을 넘습니다")

/** 수납 건이 없다. */
class PaymentNotFoundException(val paymentId: Long)
    : RuntimeException("수납 건 $paymentId 이 없습니다")
