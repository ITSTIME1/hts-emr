package com.htsemr.billing.entity

import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.reception.EligibilityView
import com.htsemr.reception.InsuranceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime

internal class InvoiceTest {

    @Nested
    inner class `건강보험 자격이면` {

        @Test
        fun `시나리오 2 - 급여 총액과 본인부담을 각각 법정 단위로 버리고 비급여는 그대로 더한다`() {
            val invoice = Invoice.calculate(nationalHealth(rate = 30), scenarioItems(), CALCULATED_AT)

            assertThat(invoice.coveredLineSum).isEqualTo(Money(31_068))
            assertThat(invoice.coveredTotal).isEqualTo(Money(31_060))
            assertThat(invoice.coveredCopay).isEqualTo(Money(9_300))
            assertThat(invoice.insurerAmount).isEqualTo(Money(21_760))
            assertThat(invoice.nonCoveredTotal).isEqualTo(Money(20_000))
            assertThat(invoice.patientAmount).isEqualTo(Money(29_300))
        }

        @Test
        fun `본인부담에서 버린 금액은 공단부담으로 옮겨 가고 총액에서 버린 금액은 어디에도 청구하지 않는다`() {
            val invoice = Invoice.calculate(nationalHealth(rate = 30), scenarioItems(), CALCULATED_AT)

            assertThat(invoice.coveredCopay + invoice.insurerAmount).isEqualTo(invoice.coveredTotal)
            assertThat(invoice.coveredLineSum - invoice.coveredTotal).isEqualTo(Money(8))
        }

        @ParameterizedTest(name = "급여 합 {0}원, 부담률 {1}% → 총액 {2}원, 본인부담 {3}원")
        @CsvSource(
            "1000,  30, 1000,  300",
            "1330,  30, 1330,  300",
            "1340,  30, 1340,  400",
            "1339,  30, 1330,  300",
            "9,     30,    0,    0",
            "31070, 33, 31070, 10200",
            "5000,   0, 5000,    0",
        )
        fun `급여 총액은 10원 미만, 본인부담은 100원 미만을 버린다`(
            coveredLineSum: Long, rate: Int, expectedTotal: Long, expectedCopay: Long,
        ) {
            val invoice = Invoice.calculate(
                nationalHealth(rate = rate),
                listOf(covered(orderItemId = 1, unitPrice = coveredLineSum)),
                CALCULATED_AT,
            )

            assertThat(invoice.coveredTotal).isEqualTo(Money(expectedTotal))
            assertThat(invoice.coveredCopay).isEqualTo(Money(expectedCopay))
            assertThat(invoice.insurerAmount).isEqualTo(Money(expectedTotal - expectedCopay))
        }

        @Test
        fun `비급여 합계는 끝전을 처리하지 않는다`() {
            val invoice = Invoice.calculate(
                nationalHealth(rate = 30),
                listOf(nonCovered(orderItemId = 1, unitPrice = 20_005)),
                CALCULATED_AT,
            )

            assertThat(invoice.nonCoveredTotal).isEqualTo(Money(20_005))
            assertThat(invoice.patientAmount).isEqualTo(Money(20_005))
        }

        @Test
        fun `급여 항목이 없으면 급여 쪽 금액은 모두 0이다`() {
            val invoice = Invoice.calculate(
                nationalHealth(rate = 30),
                listOf(nonCovered(orderItemId = 1, unitPrice = 20_000)),
                CALCULATED_AT,
            )

            assertThat(invoice.coveredLineSum).isEqualTo(Money.ZERO)
            assertThat(invoice.coveredTotal).isEqualTo(Money.ZERO)
            assertThat(invoice.coveredCopay).isEqualTo(Money.ZERO)
            assertThat(invoice.insurerAmount).isEqualTo(Money.ZERO)
        }
    }

    @Nested
    inner class `일반 자격이면` {

        @Test
        fun `시나리오 3 - 끝전을 처리하지 않고 모든 항목이 전액 본인부담이다`() {
            val invoice = Invoice.calculate(uninsured(), scenarioItems(), CALCULATED_AT)

            assertThat(invoice.coveredLineSum).isEqualTo(Money(31_068))
            assertThat(invoice.coveredTotal).isEqualTo(Money(31_068))
            assertThat(invoice.coveredCopay).isEqualTo(Money(31_068))
            assertThat(invoice.insurerAmount).isEqualTo(Money.ZERO)
            assertThat(invoice.nonCoveredTotal).isEqualTo(Money(20_000))
            assertThat(invoice.patientAmount).isEqualTo(Money(51_068))
        }
    }

    @Nested
    inner class `계산 근거로` {

        @Test
        fun `자격의 내원 건, 순번, 보험 유형, 부담률과 산정 시각을 남긴다`() {
            val eligibility = nationalHealth(rate = 30, seq = 2)

            val invoice = Invoice.calculate(eligibility, scenarioItems(), CALCULATED_AT)

            assertThat(invoice.visitId).isEqualTo(VISIT_ID)
            assertThat(invoice.eligibilitySeq).isEqualTo(2)
            assertThat(invoice.insuranceType).isEqualTo(InsuranceType.NATIONAL_HEALTH)
            assertThat(invoice.copayRate).isEqualTo(30)
            assertThat(invoice.calculatedAt).isEqualTo(CALCULATED_AT)
        }
    }

    @Nested
    inner class `산정을 거부한다` {

        @Test
        fun `대상이 비었을 때`() {
            assertThrows<IllegalArgumentException> {
                Invoice.calculate(nationalHealth(rate = 30), emptyList(), CALCULATED_AT)
            }
        }

        @Test
        fun `같은 오더 항목이 두 번 들어 있을 때`() {
            assertThrows<IllegalArgumentException> {
                Invoice.calculate(
                    nationalHealth(rate = 30),
                    listOf(covered(orderItemId = 1, unitPrice = 1_000), covered(orderItemId = 1, unitPrice = 1_000)),
                    CALCULATED_AT,
                )
            }
        }

        @ParameterizedTest
        @CsvSource("0", "-1")
        fun `수량이 1보다 작을 때`(quantity: Int) {
            assertThrows<IllegalArgumentException> {
                covered(orderItemId = 1, unitPrice = 1_000, quantity = quantity)
            }
        }
    }

    private fun scenarioItems() = listOf(
        covered(orderItemId = 1, unitPrice = 17_619),
        covered(orderItemId = 2, unitPrice = 4_483, quantity = 3),
        nonCovered(orderItemId = 3, unitPrice = 20_000),
    )

    private fun covered(orderItemId: Long, unitPrice: Long, quantity: Int = 1) =
        PricedItem(orderItemId, feeItemId = orderItemId, CoverageType.COVERED, Money(unitPrice), quantity)

    private fun nonCovered(orderItemId: Long, unitPrice: Long, quantity: Int = 1) =
        PricedItem(orderItemId, feeItemId = orderItemId, CoverageType.NON_COVERED, Money(unitPrice), quantity)

    private fun nationalHealth(rate: Int, seq: Int = 1) =
        EligibilityView(VISIT_ID, seq, InsuranceType.NATIONAL_HEALTH, rate, RECORDED_AT)

    private fun uninsured() =
        EligibilityView(VISIT_ID, 1, InsuranceType.UNINSURED, 100, RECORDED_AT)

    companion object {
        const val VISIT_ID = 1000L
        val RECORDED_AT: LocalDateTime = LocalDateTime.of(2026, 6, 2, 9, 0)
        val CALCULATED_AT: LocalDateTime = LocalDateTime.of(2026, 6, 2, 11, 30)
    }
}
