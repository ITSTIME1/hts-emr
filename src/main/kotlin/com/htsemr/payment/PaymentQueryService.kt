package com.htsemr.payment

/** 수납 건 조회. payment 모듈의 공개 기능이며 조회만 한다. */
interface PaymentQueryService {

    /**
     * 수납 건, 포함한 오더 항목, 결제. 조회 시점의 값이다. 결제는 id 순서다.
     *
     * @throws PaymentNotFoundException 수납 건이 없을 때
     */
    fun payment(paymentId: Long): PaymentView
}
