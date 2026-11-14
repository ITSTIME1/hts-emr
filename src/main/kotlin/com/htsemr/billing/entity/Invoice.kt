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

/**
 * 산정(Invoice). 만든 뒤 바뀌지 않으므로 모든 프로퍼티가 val이다.
 * 금액은 Money이며 MoneyConverter가 자동으로 원 단위 숫자 컬럼과 변환한다.
 */
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
        /** 요양급여비용총액의 절사 단위. 10원 미만을 버린다(A-30). */
        const val COVERED_TOTAL_UNIT = 10L

        /** 외래 본인일부부담금의 절사 단위. 100원 미만을 버린다(A-30). */
        const val OUTPATIENT_COPAY_UNIT = 100L

        /**
         * 산정(F-03). 자격과 항목들로 합계를 모두 계산한다.
         *
         * 생성자를 막고 이 메서드로만 만드는 이유: 합계끼리의 관계(공단부담 = 총액 − 본인부담 등)를
         * 여기서 한 번에 계산하므로, 서로 어긋난 합계를 가진 산정이 만들어질 길이 없다(V6의 체크 제약과 같은 규칙).
         * 내원 건 id는 자격에서 가져온다. 따로 받으면 다른 내원 건의 자격과 섞일 수 있다.
         */
        fun calculate(eligibility: EligibilityView, items: List<PricedItem>, calculatedAt: LocalDateTime): Invoice {
            require(items.isNotEmpty()) { "산정 대상이 없습니다" }
            require(items.map { it.orderItemId }.toSet().size == items.size) { "같은 오더 항목이 두 번 들어 있습니다" }

            val coveredLineSum = sumOf(items.filter { it.coverageType == CoverageType.COVERED })
            val nonCoveredTotal = sumOf(items.filter { it.coverageType == CoverageType.NON_COVERED })

            // when에 else를 두지 않는다. 보험 유형이 늘어나면 여기서 컴파일 오류가 나서 계산을 빠뜨릴 수 없다.
            val (coveredTotal, coveredCopay) = when (eligibility.insuranceType) {
                InsuranceType.NATIONAL_HEALTH -> {
                    val total = truncate(coveredLineSum, COVERED_TOTAL_UNIT)
                    // 곱한 뒤 나눈다. 먼저 나누면 정수 나눗셈에서 끝자리가 사라진다.
                    val copayBeforeRounding = Money(total.amount * eligibility.copayRate / 100)
                    total to truncate(copayBeforeRounding, OUTPATIENT_COPAY_UNIT)
                }
                // 요양급여가 아니므로 끝전을 처리하지 않고 전액 본인부담이다(A-31).
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

        /** unit 미만을 버린다. 금액이 0 이상이므로 정수 나눗셈의 버림과 같다. */
        private fun truncate(money: Money, unit: Long) = Money(money.amount / unit * unit)
    }
}
