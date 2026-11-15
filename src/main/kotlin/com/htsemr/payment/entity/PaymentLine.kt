package com.htsemr.payment.entity

import com.htsemr.common.Money
import com.htsemr.payment.PaymentLineStatus
import com.htsemr.payment.PaymentMethod
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 결제(PaymentLine). 수납 건의 낼 돈 가운데 일부 또는 전부를 한 수단으로 받은 기록이다.
 * 수납 건을 거쳐서만 만든다([Payment.receiveCash]). 어느 수납 건의 것인지(payment_id)는 수납 건 쪽의 연관이 채운다.
 */
@Entity
@Table(name = "payment_line")
internal class PaymentLine private constructor(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val method: PaymentMethod,

    @Column(nullable = false) val receivedAmount: Money,
    @Column(nullable = false) val amount: Money,
    @Column(nullable = false) val receivedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 지금 상태. 현금 반환(T-PLN-13)은 다음 슬라이스에서 전이 메서드로 만든다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentLineStatus = PaymentLineStatus.RECEIVED
        protected set

    /** 거스름돈. 저장하지 않고 계산한다(V7 ck_payment_line_received가 음수를 막는다). */
    val change: Money get() = receivedAmount - amount

    companion object {
        /** 현금 수령(T-PLN-12). 받은 금액은 결제 금액보다 작을 수 없다(거스름돈 ≥ 0). */
        fun cash(received: Money, amount: Money, receivedAt: LocalDateTime): PaymentLine {
            require(amount > Money.ZERO) { "결제 금액은 0보다 커야 합니다: ${amount.amount}" }
            require(received >= amount) { "받은 금액(${received.amount})이 결제 금액(${amount.amount})보다 작습니다" }
            return PaymentLine(PaymentMethod.CASH, received, amount, receivedAt)
        }
    }
}
