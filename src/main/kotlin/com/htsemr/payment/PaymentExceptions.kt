package com.htsemr.payment

import com.htsemr.common.Money

/** 수납할 오더 항목이 없다. 효력 있는 항목이 없거나, 모두 완료된 수납 건에 들어 있다(A-02). */
class NothingToPayException(val visitId: Long)
    : RuntimeException("내원 건 $visitId 에 수납할 오더 항목이 없습니다")

/** 이 결제를 더하면 결제 합계가 본인부담 합계를 넘는다(A-20). */
class PaymentAmountExceededException(val invoiceId: Long, val patientAmount: Money, val paidAmountAfter: Money)
    : RuntimeException("산정 $invoiceId: 결제 합계 ${paidAmountAfter.amount}원이 본인부담 합계 ${patientAmount.amount}원을 넘습니다")

/**
 * 이 산정은 이미 수납 건에 쓰였다(A-22, INV-05). 한 산정으로는 수납 건 하나다. 다시 받으려면 새로 산정한다.
 * 진행 중인 수납 건은 이어서 쓰므로, 이 예외는 그 산정의 수납 건이 이미 끝났을 때 난다.
 * 끝난 이유는 완료, 거부, 보류 가운데 하나다(슬라이스 1에서는 완료뿐). 그래서 "이미 냈다"가 아니라 "이미 쓰였다"다.
 */
class InvoiceAlreadyUsedException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 으로 만든 수납 건이 이미 있습니다. 다시 받으려면 새로 산정하세요")

/**
 * 다른 창구가 같은 산정으로 같은 순간에 수납 건을 먼저 만들었다(C-06, INV-05).
 * 이 호출의 결제는 저장되지 않았다. 다시 시도하면 먼저 만든 수납 건을 보고 그 결과를 따른다.
 * 진행 중이면 거기에 이어서 받고, 이미 끝났으면 [InvoiceAlreadyUsedException]이 난다.
 *
 * [InvoiceAlreadyUsedException]과의 차이: 그쪽은 확인할 때 이미 수납 건이 보인 경우라 다시 시도해도 소용없다.
 * 이쪽은 확인할 때는 없었는데 저장하는 순간 겹친 경우라, 다시 시도하면 된다.
 */
class PaymentStartConflictException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 으로 다른 창구에서 방금 수납을 시작했습니다. 다시 시도하세요")

/** 수납 건이 없다. */
class PaymentNotFoundException(val paymentId: Long)
    : RuntimeException("수납 건 $paymentId 이 없습니다")
