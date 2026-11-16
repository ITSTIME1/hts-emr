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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentLineStatus = PaymentLineStatus.RECEIVED
        protected set

    val change: Money get() = receivedAmount - amount

    companion object {
        fun cash(received: Money, amount: Money, receivedAt: LocalDateTime): PaymentLine {
            require(amount > Money.ZERO) { "결제 금액은 0보다 커야 합니다: ${amount.amount}" }
            require(received >= amount) { "받은 금액(${received.amount})이 결제 금액(${amount.amount})보다 작습니다" }
            return PaymentLine(PaymentMethod.CASH, received, amount, receivedAt)
        }
    }
}
