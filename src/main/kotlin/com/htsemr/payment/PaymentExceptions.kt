package com.htsemr.payment

import com.htsemr.common.Money

class NothingToPayException(val visitId: Long)
    : RuntimeException("내원 건 $visitId 에 수납할 오더 항목이 없습니다")

class PaymentAmountExceededException(val invoiceId: Long, val patientAmount: Money, val paidAmountAfter: Money)
    : RuntimeException("산정 $invoiceId: 결제 합계 ${paidAmountAfter.amount}원이 본인부담 합계 ${patientAmount.amount}원을 넘습니다")

class InvoiceAlreadyUsedException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 으로 만든 수납 건이 이미 있습니다. 다시 받으려면 새로 산정하세요")

class PaymentStartConflictException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 으로 다른 창구에서 방금 수납을 시작했습니다. 다시 시도하세요")

class PaymentCompletionConflictException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 의 수납 건과 같은 항목을 다른 수납 건이 방금 완료했습니다. 이 결제는 저장되지 않았습니다")

class PaymentNotFoundException(val paymentId: Long)
    : RuntimeException("수납 건 $paymentId 이 없습니다")
