package com.htsemr.billing.entity

import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.reception.EligibilityView
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
@Table(name = "invoice")
internal class Invoice private constructor(
    @Column(nullable = false) val visitId: Long,
    @Column(nullable = false) val eligibilitySeq: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val insuranceType: InsuranceType,

    @Column(nullable = false) val copayRate: Int,
    @Column(nullable = false) val coveredLineSum: Money,
    @Column(nullable = false) val coveredTotal: Money,
    @Column(nullable = false) val coveredCopay: Money,
    @Column(nullable = false) val insurerAmount: Money,
    @Column(nullable = false) val nonCoveredTotal: Money,
    @Column(nullable = false) val patientAmount: Money,
    @Column(nullable = false) val calculatedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        const val COVERED_TOTAL_UNIT = 10L

        const val OUTPATIENT_COPAY_UNIT = 100L

        fun calculate(eligibility: EligibilityView, items: List<PricedItem>, calculatedAt: LocalDateTime): Invoice {
            require(items.isNotEmpty()) { "산정 대상이 없습니다" }
            require(items.map { it.orderItemId }.toSet().size == items.size) { "같은 오더 항목이 두 번 들어 있습니다" }

            val coveredLineSum = sumOf(items.filter { it.coverageType == CoverageType.COVERED })
            val nonCoveredTotal = sumOf(items.filter { it.coverageType == CoverageType.NON_COVERED })

            val (coveredTotal, coveredCopay) = when (eligibility.insuranceType) {
                InsuranceType.NATIONAL_HEALTH -> {
                    val total = truncate(coveredLineSum, COVERED_TOTAL_UNIT)
                    val copayBeforeRounding = Money(total.amount * eligibility.copayRate / 100)
                    total to truncate(copayBeforeRounding, OUTPATIENT_COPAY_UNIT)
                }
                InsuranceType.UNINSURED -> coveredLineSum to coveredLineSum
            }

            return Invoice(
                visitId = eligibility.visitId,
                eligibilitySeq = eligibility.seq,
                insuranceType = eligibility.insuranceType,
                copayRate = eligibility.copayRate,
                coveredLineSum = coveredLineSum,
                coveredTotal = coveredTotal,
                coveredCopay = coveredCopay,
                insurerAmount = coveredTotal - coveredCopay,
                nonCoveredTotal = nonCoveredTotal,
                patientAmount = coveredCopay + nonCoveredTotal,
                calculatedAt = calculatedAt,
            )
        }

        private fun sumOf(items: List<PricedItem>): Money =
            items.fold(Money.ZERO) { sum, item -> sum + item.lineTotal }

        private fun truncate(money: Money, unit: Long) = Money(money.amount / unit * unit)
    }
}
