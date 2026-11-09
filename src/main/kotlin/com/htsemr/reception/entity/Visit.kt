package com.htsemr.reception.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 내원 건(Visit). 환자가 한 진료일에 한 진료과로 접수해 생기는 진료 단위(F-01).
 * 같은 환자·진료일·진료과에는 하나만 있다(DB 유일 제약).
 *
 * 설계: docs/modules/reception.md
 */
@Entity
@Table(name = "visit")
internal class Visit(

    /** 환자, 진료과, 진료 의사는 다른 시스템에서 받은 값이다. 이 프로젝트에 해당 테이블은 없다(A-29). */
    @Column(nullable = false)
    val patientNo: String,

    @Column(nullable = false)
    val departmentCode: String,

    @Column(nullable = false)
    val doctorNo: String,

    /** 진료일. 수가 적용(A-06)과 진료일 경과 판단의 기준이다. 접수 시각과 구분한다. */
    @Column(nullable = false)
    val visitDate: LocalDate,

    @Column(nullable = false)
    val registeredAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
