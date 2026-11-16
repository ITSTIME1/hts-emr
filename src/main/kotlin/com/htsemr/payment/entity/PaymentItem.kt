package com.htsemr.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.NumericBooleanConverter

@Entity
@Table(name = "payment_item")
internal class PaymentItem(
    @Column(nullable = false) val orderItemId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Convert(converter = NumericBooleanConverter::class)
    @Column(nullable = false)
    var completed: Boolean = false
        protected set

    internal fun markCompleted() {
        completed = true
    }
}
