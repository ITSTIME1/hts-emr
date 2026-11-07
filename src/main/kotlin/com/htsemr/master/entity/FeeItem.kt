package com.htsemr.master.entity

import com.htsemr.master.CoverageType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.NumericBooleanConverter

/**
 * 수가 항목(FeeItem). 진찰·검사·처치처럼 진료비를 받을 수 있는 행위의 종류.
 * 다른 모듈이 과거 오더에서 가리키므로 삭제하지 않고, 더 쓰지 않으면 사용 종료일로 막는다(A-28).
 *
 * 설계: docs/modules/master.md
 */
@Entity
@Table(name = "fee_item")
internal class FeeItem(
    @Column(nullable = false, unique = true)
    val code : String,

    @Column(nullable = false)
    val name : String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val coverageType : CoverageType,

    /**
     * 선시행 기본값. true면 의사가 따로 승인하지 않아도 선시행 대상이다(A-08).
     *
     * DB에는 NUMBER(1)의 0/1로 저장한다. Oracle 19c 이하에는 BOOLEAN 타입이 없기 때문이다.
     * 변환기를 빼면 Hibernate가 Oracle 23에서 BOOLEAN으로 매핑하려 해 스키마 검증(validate)이 실패한다.
     */
    @Convert(converter = NumericBooleanConverter::class)
    @Column(nullable = false)
    val executableBeforePayment: Boolean,
    ) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
