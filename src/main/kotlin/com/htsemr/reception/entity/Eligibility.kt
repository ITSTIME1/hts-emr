package com.htsemr.reception.entity

import com.htsemr.reception.InsuranceType
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
@Table(name = "eligibility")
internal class Eligibility(

    @Column(nullable = false)
    val visitId: Long,

    @Column(nullable = false)
    val seq: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val insuranceType: InsuranceType,

    @Column(nullable = false)
    val copayRate: Int,

    @Column(nullable = false)
    val recordedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
