package com.htsemr.payment

interface PaymentQueryService {

    fun payment(paymentId: Long): PaymentView
}
