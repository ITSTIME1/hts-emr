package com.htsemr.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "medical_order")
internal class MedicalOrder(
    @Column(nullable = false)
    val visitId: Long,
    @Column(nullable = false)
    val doctorNo: String,
    @Column(nullable = false)
    val orderedAt: LocalDateTime,
    )
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
