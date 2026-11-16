package com.htsemr.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.NumericBooleanConverter

/**
 * 수납 건이 포함한 오더 항목 하나. 수납 건을 만들 때 산정 내역에서 복사한다. 어떤 항목인지는 바뀌지 않는다.
 * 어느 수납 건의 것인지(payment_id)는 수납 건 쪽의 연관이 채운다.
 */
@Entity
@Table(name = "payment_item")
internal class PaymentItem(
    @Column(nullable = false) val orderItemId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /**
     * 완료된 수납 건의 항목인지. 수납 건이 완료할 때 표시한다.
     *
     * 수납 건의 상태와 같은 사실을 항목에도 두는 이유: DB가 "완료된 항목끼리만 유일"(INV-03)을 지키려면
     * 그 사실이 이 테이블에 있어야 한다(V10의 uk_payment_item_completed). 동시에 완료하는 두 수납 건을 막는 마지막 방어선이다(C-03).
     * protected set인 이유는 Payment.status와 같다.
     */
    @Convert(converter = NumericBooleanConverter::class)
    @Column(nullable = false)
    var completed: Boolean = false
        protected set

    internal fun markCompleted() {
        completed = true
    }
}
