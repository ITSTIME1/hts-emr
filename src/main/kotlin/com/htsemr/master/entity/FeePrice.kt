package com.htsemr.master.entity

import com.htsemr.common.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 수가(FeePrice).
 * 수가 항목의 단가 이력.
 * 적용을 시작한 단가는 고치지 않고, 바뀌면 새로 추가한다(A-28).
 *
 * @see <a href="https://github.com/.../docs/modules/master.md">수가 설계</a>
 */
@Entity
@Table(name = "fee_price")
internal class FeePrice(

    /**
     * [연관관계 대신 ID로 참조]
     * (e.g val feeItemId : FeeItemId @ManyToOne(fetch = FetchType.LAZY)
     * 같은 모듈, 다른 집합체이므로 id 참조를 사용.
     */
    @Column(nullable = false)
    val feeItemId: Long,

    /**
     * 진료일 이전 가운데 가장 늦은 것이 적용된다(A-06).
     * 같은 수가 항목에 같은 날짜는 하나뿐이다(DB 유일 제약).
     */
    @Column(nullable = false)
    val effectiveFrom: LocalDate,

    @Column(nullable = false)
    val amount: Money,              // MoneyConverter가 자동 적용됨 (@Converter(autoApply = true))
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}