package com.htsemr.master.entity

import com.htsemr.common.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "fee_price")
internal class FeePrice(
    @Column(nullable = false)
    val feeItemId: Long,            // 연관관계 대신 ID로 참조

    @Column(nullable = false)
    val effectiveFrom: LocalDate,

    @Column(nullable = false)
    val amount: Money,              // MoneyConverter가 자동 적용됨 (@Converter(autoApply = true))
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}