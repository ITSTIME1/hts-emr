package com.htsemr.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 수납 건이 포함한 오더 항목 하나. 수납 건을 만들 때 산정 내역에서 복사하고, 바뀌지 않는다.
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
}
