package com.htsemr.payment.entity

import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceView
import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.order.OrderItemStatus
import com.htsemr.payment.PaymentAmountExceededException
import com.htsemr.payment.PaymentStatus
import com.htsemr.reception.InsuranceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime

internal class PaymentTest {

    @Nested
    inner class `수납 건을 만들면` {

        @Test
        fun `산정의 내원 건, 낼 돈, 오더 항목을 복사하고 진행 중이다`() {
            val payment = Payment.start(invoice(), COUNTER_ID, CASHIER_NO, NOW)

            assertThat(payment.invoiceId).isEqualTo(INVOICE_ID)
            assertThat(payment.visitId).isEqualTo(VISIT_ID)
            assertThat(payment.patientAmount).isEqualTo(Money(29_300))
            assertThat(payment.orderItemIds).containsExactly(501L, 502L, 503L)
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(payment.completedAt).isNull()
            assertThat(payment.paidAmount).isEqualTo(Money.ZERO)
        }

        @Test
        fun `수납자 번호가 비었으면 거부한다`() {
            assertThrows<IllegalArgumentException> { Payment.start(invoice(), COUNTER_ID, " ", NOW) }
        }
    }

    @Nested
    inner class `현금을 받으면` {

        @Test
        fun `결제 금액만 결제 합계에 더하고 거스름돈은 받은 금액과의 차이다`() {
            val payment = started()

            payment.receiveCash(received = Money(20_000), amount = Money(20_000), receivedAt = NOW)
            payment.receiveCash(received = Money(10_000), amount = Money(9_300), receivedAt = NOW)

            assertThat(payment.paidAmount).isEqualTo(Money(29_300))
            assertThat(payment.remaining).isEqualTo(Money.ZERO)
            assertThat(payment.isFullyPaid).isTrue()
            assertThat(payment.lines.map { it.change }).containsExactly(Money.ZERO, Money(700))
        }

        @Test
        fun `결제 합계가 낼 돈을 넘으면 거부하고 결제를 더하지 않는다`() {
            val payment = started()
            payment.receiveCash(Money(20_000), Money(20_000), NOW)

            val exception = assertThrows<PaymentAmountExceededException> {
                payment.receiveCash(Money(10_000), Money(9_301), NOW)
            }

            assertThat(exception.paidAmountAfter).isEqualTo(Money(29_301))
            assertThat(payment.lines).hasSize(1)
        }

        @Test
        fun `결제 금액이 0이면 거부한다`() {
            assertThrows<IllegalArgumentException> { started().receiveCash(Money(1_000), Money.ZERO, NOW) }
        }

        @Test
        fun `받은 금액이 결제 금액보다 작으면 거부한다`() {
            assertThrows<IllegalArgumentException> { started().receiveCash(Money(9_000), Money(9_300), NOW) }
        }

        @Test
        fun `완료된 수납 건에는 받지 않는다`() {
            val payment = fullyPaid().apply { complete(allEffective(), emptySet(), NOW) }

            assertThrows<IllegalStateException> { payment.receiveCash(Money(1_000), Money(1_000), NOW) }
        }
    }

    @Nested
    inner class `완료하면` {

        @Test
        fun `항목이 모두 효력이 있고 다른 수납 건에 없으면 완료하고 완료 시각을 남긴다`() {
            val payment = fullyPaid()

            val blockers = payment.complete(allEffective(), emptySet(), COMPLETED_AT)

            assertThat(blockers.isEmpty).isTrue()
            assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED)
            assertThat(payment.completedAt).isEqualTo(COMPLETED_AT)
        }

        @ParameterizedTest
        @EnumSource(OrderItemStatus::class, names = ["CANCEL_REQUESTED", "CANCELLED", "EXPIRED"])
        fun `효력 없는 항목이 있으면 완료하지 않고 그 항목을 알린다`(ineffectiveStatus: OrderItemStatus) {
            val payment = fullyPaid()
            val statuses = allEffective() + (502L to ineffectiveStatus)

            val blockers = payment.complete(statuses, emptySet(), COMPLETED_AT)

            assertThat(blockers.ineffectiveItemIds).containsExactly(502L)
            assertThat(blockers.alreadyPaidItemIds).isEmpty()
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(payment.completedAt).isNull()
            assertThat(payment.lines).hasSize(2)
        }

        @Test
        fun `다른 완료된 수납 건에 이미 들어 있는 항목이 있으면 완료하지 않고 그 항목을 알린다`() {
            val payment = fullyPaid()

            val blockers = payment.complete(allEffective(), setOf(501L, 503L, 999L), COMPLETED_AT)

            assertThat(blockers.alreadyPaidItemIds).containsExactlyInAnyOrder(501L, 503L)
            assertThat(blockers.ineffectiveItemIds).isEmpty()
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(payment.completedAt).isNull()
            assertThat(payment.lines).hasSize(2)
        }

        @Test
        fun `두 이유에 함께 걸리면 둘 다 알린다`() {
            val payment = fullyPaid()
            val statuses = allEffective() + (502L to OrderItemStatus.CANCELLED)

            val blockers = payment.complete(statuses, setOf(501L), COMPLETED_AT)

            assertThat(blockers.ineffectiveItemIds).containsExactly(502L)
            assertThat(blockers.alreadyPaidItemIds).containsExactly(501L)
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @Test
        fun `결제 합계가 낼 돈에 이르지 않았으면 완료할 수 없다`() {
            val payment = started().apply { receiveCash(Money(20_000), Money(20_000), NOW) }

            assertThrows<IllegalStateException> { payment.complete(allEffective(), emptySet(), COMPLETED_AT) }
        }
    }

    private fun started() = Payment.start(invoice(), COUNTER_ID, CASHIER_NO, NOW)

    private fun fullyPaid() = started().apply {
        receiveCash(Money(20_000), Money(20_000), NOW)
        receiveCash(Money(10_000), Money(9_300), NOW)
    }

    private fun allEffective() = mapOf(
        501L to OrderItemStatus.EXECUTED,
        502L to OrderItemStatus.ORDERED,
        503L to OrderItemStatus.ORDERED,
    )

    private fun invoice() = InvoiceView(
        id = INVOICE_ID, visitId = VISIT_ID, eligibilitySeq = 1,
        insuranceType = InsuranceType.NATIONAL_HEALTH, copayRate = 30,
        coveredLineSum = Money(31_068), coveredTotal = Money(31_060), coveredCopay = Money(9_300),
        insurerAmount = Money(21_760), nonCoveredTotal = Money(20_000), patientAmount = Money(29_300),
        calculatedAt = NOW,
        lines = listOf(
            InvoiceLineView(1, 501, 11, CoverageType.COVERED, Money(17_619), 1, Money(17_619)),
            InvoiceLineView(2, 502, 22, CoverageType.COVERED, Money(4_483), 3, Money(13_449)),
            InvoiceLineView(3, 503, 33, CoverageType.NON_COVERED, Money(20_000), 1, Money(20_000)),
        ),
    )

    companion object {
        const val INVOICE_ID = 1L
        const val VISIT_ID = 1000L
        const val COUNTER_ID = 7L
        const val CASHIER_NO = "T-C01"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 2, 11, 30)
        val COMPLETED_AT: LocalDateTime = LocalDateTime.of(2026, 6, 2, 11, 35)
    }
}
