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

/**
 * 자격 기록(Eligibility). 내원 건에 남긴 보험 자격.
 * 환자의 지금 자격이 아니라 그 내원 건에 대한 기록이므로, 추가만 하고 고치거나 지우지 않는다(A-03).
 *
 * 설계: docs/modules/reception.md
 */
@Entity
@Table(name = "eligibility")
internal class Eligibility(

    /**
     * [연관관계 대신 ID로 참조]
     * 조회는 항상 내원 건 id에서 시작하고, 자격 기록에서 내원 건으로 거슬러 갈 일이 없다.
     * Visit에 자격 기록 컬렉션을 두면 기록을 고치는 경로가 생기므로 두지 않는다.
     * 없는 내원 건을 가리키지 않는 것은 DB 외래 키가 지킨다.
     */
    @Column(nullable = false)
    val visitId: Long,

    /** 내원 건 안의 순번. 1부터 시작하고 가장 큰 값이 현재 자격이다. 같은 순번은 하나뿐이다(DB 유일 제약). */
    @Column(nullable = false)
    val seq: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val insuranceType: InsuranceType,

    /** 본인부담률. 정수 퍼센트(0~100). 일반 자격은 100이다(DB 제약). */
    @Column(nullable = false)
    val copayRate: Int,

    @Column(nullable = false)
    val recordedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
