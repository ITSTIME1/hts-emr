package com.htsemr.payment.service

import com.htsemr.payment.PaymentNotFoundException
import com.htsemr.payment.PaymentQueryService
import com.htsemr.payment.PaymentView
import com.htsemr.payment.mapping.toPaymentView
import com.htsemr.payment.repository.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
internal class PaymentQueryServiceImpl(
    private val paymentRepository: PaymentRepository,
) : PaymentQueryService {

    /** 결제와 항목은 지연 로딩이므로, 트랜잭션 안에서 공개 타입으로 바꿔 돌려준다. */
    override fun payment(paymentId: Long): PaymentView =
        (paymentRepository.findByIdOrNull(paymentId) ?: throw PaymentNotFoundException(paymentId)).toPaymentView()
}
