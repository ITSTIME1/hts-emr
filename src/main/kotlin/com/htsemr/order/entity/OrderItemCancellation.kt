package com.htsemr.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "order_item_cancellation")
internal class OrderItemCancellation(
    @Column(nullable = false) val orderItemId: Long,
    @Column(nullable = false) val doctorNo: String,
    @Column(nullable = false) val reason: String,
    @Column(nullable = false) val cancelledAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}