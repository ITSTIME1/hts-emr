package com.htsemr.reception.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "visit")
internal class Visit(

    @Column(nullable = false)
    val patientNo: String,

    @Column(nullable = false)
    val departmentCode: String,

    @Column(nullable = false)
    val doctorNo: String,

    @Column(nullable = false)
    val visitDate: LocalDate,

    @Column(nullable = false)
    val registeredAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
