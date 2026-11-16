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
    val feeItemId: Long,

    @Column(nullable = false)
    val effectiveFrom: LocalDate,

    @Column(nullable = false)
    val amount: Money,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
