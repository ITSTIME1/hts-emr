package com.htsemr.payment

import com.htsemr.TestcontainersConfiguration
import com.htsemr.billing.BillingService
import com.htsemr.billing.BillingTarget
import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.billing.InvoiceView
import com.htsemr.common.Money
import com.htsemr.master.CounterNotFoundException
import com.htsemr.master.CounterQueryService
import com.htsemr.master.CounterView
import com.htsemr.master.CoverageType
import com.htsemr.order.OrderItemQueryService
import com.htsemr.order.OrderItemStatus
import com.htsemr.order.OrderItemView
import com.htsemr.payment.repository.PaymentRepository
import com.htsemr.reception.InsuranceType
import com.htsemr.reception.VisitNotFoundException
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 산정 요청(requestInvoice), 현금 수납(receiveCash), 수납 건 조회(payment) 테스트.
 *
 * 수납 건 하나의 규칙(A-20, 완료 조건)은 PaymentTest가 DB 없이 확인한다. 여기서는 다른 모듈의 조회 결과와 이어지는지,
 * 저장·조회와 트랜잭션(특히 완료가 막혀도 받은 현금이 남는지)이 기대대로 동작하는지 본다.
 *
 * 다른 모듈(reception, order, billing, master)은 대역으로 채운다. 서비스가 자기 트랜잭션을 커밋하므로
 * 클래스에 @Transactional을 붙이지 않고, 테스트마다 직접 지운다(OrderServiceTest, BillingServiceTest와 같은 이유).
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class PaymentServiceTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentQueryService: PaymentQueryService,
    private val paymentRepository: PaymentRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @MockitoBean private lateinit var visitQueryService: VisitQueryService
    @MockitoBean private lateinit var orderItemQueryService: OrderItemQueryService
    @MockitoBean private lateinit var billingService: BillingService
    @MockitoBean private lateinit var invoiceQueryService: InvoiceQueryService
    @MockitoBean private lateinit var counterQueryService: CounterQueryService

    /**
     * 기본 응답: 내원 건 하나, 오더 항목 셋(시나리오 2: 501 진찰료 EXECUTED, 502 혈액검사 ORDERED, 503 진단서 ORDERED),
     * 그 셋으로 만든 산정(낼 돈 29,300원), 창구 하나, 셋 다 효력 있는 지금 상태.
     * 다른 응답이 필요한 테스트만 덮어쓴다.
     */
    @BeforeEach
    fun stubOtherModules() {
        given(visitQueryService.visit(VISIT_ID))
            .willReturn(VisitView(VISIT_ID, "T-P001", "T-IM", "T-D001", VISIT_DATE))
        given(orderItemQueryService.itemsOfVisit(VISIT_ID)).willReturn(scenarioItems())
        given(billingService.calculate(VISIT_ID, scenarioTargets())).willReturn(INVOICE_ID)
        given(invoiceQueryService.invoice(INVOICE_ID)).willReturn(scenarioInvoice())
        given(counterQueryService.counter(COUNTER_ID)).willReturn(CounterView(COUNTER_ID, "T-C1", "1번 창구"))
        given(orderItemQueryService.statusesOf(listOf(501L, 502L, 503L))).willReturn(
            mapOf(501L to OrderItemStatus.EXECUTED, 502L to OrderItemStatus.ORDERED, 503L to OrderItemStatus.ORDERED)
        )
    }

    /** 결제와 항목은 수납 건의 cascade로 함께 지워진다. */
    @AfterEach
    fun cleanUp() {
        transactionTemplate.executeWithoutResult { paymentRepository.deleteAll() }
    }

    @Nested
    inner class `산정을 요청하면` {

        @Test
        fun `효력 있고 수납하지 않은 항목으로 billing에 산정을 요청하고 산정 id를 돌려준다`() {
            // then: 반환값 INVOICE_ID, then(billingService).should().calculate(VISIT_ID, scenarioTargets())
            val invoiceId = paymentService.requestInvoice(visitId = VISIT_ID)
            assertThat(invoiceId).isEqualTo(INVOICE_ID)
        }

        @Test
        fun `효력 없는 항목은 빼고 요청한다`() {
            /**
             * effectiveItems는 filter 조건에 it.status.isEffective 필터링해서 집합을 만든다.
             * 그래서 유효하지 않은 상태값(CANCELLED, CANCEL_REQUESTED, EXPIRED)은 제외하고 ORDERED, EXECUTED만 남기게 된다.
             * 그러므로, scenarioItems() 메소드에 임시 데이터에 item()을 호출해 위 3가지 데이터 타입이 존재한다면,
             * 산정을 요청하고, billing..calculate()에 넘어가는 인자는 OrderItemStatus의 isEffective = true인 항목만 선별해서 넘어간다.
             *
             */
            given(orderItemQueryService.itemsOfVisit(VISIT_ID))
                .willReturn(scenarioItems() + item(504, FEE_CERT, 1, OrderItemStatus.CANCELLED) + item(505, FEE_CERT, 1, OrderItemStatus.CANCEL_REQUESTED) + item(506, FEE_CERT, 1, OrderItemStatus.EXPIRED))

            val invoiceId = paymentService.requestInvoice(visitId = VISIT_ID)


            assertThat(invoiceId).isEqualTo(INVOICE_ID)
            then(billingService).should().calculate(VISIT_ID, scenarioTargets())
        }


        /**
         * 실제 잡아내는지 확인하는 테스트. 돌려보고 다시 주석 처리 Actual과 Wanted가 서로 다르고 잡아내는 BillingTarget이 있다면 확인.
         */
//        @Test
//        fun `효력 없는 항목을 빼고 요청했을 때 의도적으로 실패해서 넘어간 인자를 확인한다`() {
//            /**
//             * effectiveItems는 filter 조건에 it.status.isEffective 필터링해서 집합을 만든다.
//             * 그래서 유효하지 않은 상태값(CANCELLED, CANCEL_REQUESTED, EXPIRED)은 제외하고 ORDERED, EXECUTED만 남기게 된다.
//             * 그러므로, scenarioItems() 메소드에 임시 데이터에 item()을 호출해 위 3가지 데이터 타입이 존재한다면,
//             * 산정을 요청하고, billing..calculate()에 넘어가는 인자는 OrderItemStatus의 isEffective = true인 항목만 선별해서 넘어간다.
//             *
//             */
//            given(orderItemQueryService.itemsOfVisit(VISIT_ID))
//                .willReturn(scenarioItems() + item(504, FEE_CERT, 1, OrderItemStatus.CANCELLED) + item(505, FEE_CERT, 1, OrderItemStatus.CANCEL_REQUESTED) + item(506, FEE_CERT, 1, OrderItemStatus.EXPIRED))
//
//            val invoiceId = paymentService.requestInvoice(visitId = VISIT_ID)
//
//
//            assertThat(invoiceId).isEqualTo(INVOICE_ID)
//            then(billingService).should().calculate(VISIT_ID, scenarioTargets() + BillingTarget(orderItemId = 504, feeItemId = FEE_CERT, quantity = 1))
//        }


        @Test
        fun `완료된 수납 건에 들어 있는 항목은 빼고 요청한다`() {
            // 501~503은 이미 수납을 마쳤고, 그 뒤 의사가 혈액검사 505를 추가로 냈다(A-02).
            payFully()
            val newTarget = BillingTarget(505, FEE_BLOOD, 1)
            given(orderItemQueryService.itemsOfVisit(VISIT_ID))
                .willReturn(scenarioItems() + item(505, FEE_BLOOD, 1, OrderItemStatus.ORDERED))
            given(billingService.calculate(VISIT_ID, listOf(newTarget))).willReturn(SECOND_INVOICE_ID)

            val invoiceId = paymentService.requestInvoice(VISIT_ID)

            assertThat(invoiceId).isEqualTo(SECOND_INVOICE_ID)
            then(billingService).should().calculate(VISIT_ID, listOf(newTarget))
        }

        @Test
        fun `진행 중인 수납 건의 항목은 다시 산정할 수 있다`() {
            // INV-03은 완료된 수납 건만 따지므로 정상 동작이다. 두 번 완료되는 것은 receiveCash의 완료 단계가 막는다.
            paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))
            given(billingService.calculate(VISIT_ID, scenarioTargets())).willReturn(SECOND_INVOICE_ID)

            val invoiceId = paymentService.requestInvoice(VISIT_ID)

            assertThat(invoiceId).isEqualTo(SECOND_INVOICE_ID)
            then(billingService).should().calculate(VISIT_ID, scenarioTargets())
        }

        @Test
        fun `수납할 항목이 없으면 NothingToPayException이 발생하고 billing을 부르지 않는다`() {
            given(orderItemQueryService.itemsOfVisit(VISIT_ID))
                .willReturn(scenarioItems().map { it.copy(status = OrderItemStatus.CANCELLED) })

            val exception = assertThrows<NothingToPayException> { paymentService.requestInvoice(VISIT_ID) }

            assertThat(exception.visitId).isEqualTo(VISIT_ID)
            then(billingService).shouldHaveNoInteractions()
        }

        @Test
        fun `항목이 모두 수납 완료됐으면 NothingToPayException이 발생하고 billing을 부르지 않는다`() {
            payFully()

            val exception = assertThrows<NothingToPayException> { paymentService.requestInvoice(VISIT_ID) }

            assertThat(exception.visitId).isEqualTo(VISIT_ID)
            then(billingService).shouldHaveNoInteractions()
        }

        @Test
        fun `내원 건이 없으면 VisitNotFoundException이 발생한다`() {
            given(visitQueryService.visit(UNKNOWN_ID)).willThrow(VisitNotFoundException(UNKNOWN_ID))

            val exception = assertThrows<VisitNotFoundException> { paymentService.requestInvoice(UNKNOWN_ID) }

            assertThat(exception.visitId).isEqualTo(UNKNOWN_ID)
            then(billingService).shouldHaveNoInteractions()
        }
    }

    @Nested
    inner class `현금을 받으면` {

        @Test
        fun `첫 결제면 수납 건을 만들고 남은 금액을 알린다`() {
            val result = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))

            assertThat(result).isEqualTo(CashResult.InProgress(result.paymentId, remaining = Money(9_300)))

            val view = paymentQueryService.payment(result.paymentId)
            assertThat(view.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(view.invoiceId).isEqualTo(INVOICE_ID)
            assertThat(view.visitId).isEqualTo(VISIT_ID)
            assertThat(view.counterId).isEqualTo(COUNTER_ID)
            assertThat(view.cashierNo).isEqualTo(CASHIER_NO)
            assertThat(view.patientAmount).isEqualTo(Money(29_300))
            assertThat(view.paidAmount).isEqualTo(Money(20_000))
            assertThat(view.orderItemIds).containsExactly(501L, 502L, 503L)
            assertThat(view.lines).hasSize(1)
            assertThat(view.completedAt).isNull()
        }

        @Test
        fun `같은 산정의 두 번째 결제는 같은 수납 건에 더한다`() {
            val first = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(10_000), Money(10_000))
            val second = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(5_000), Money(5_000))

            assertThat(second).isEqualTo(CashResult.InProgress(first.paymentId, remaining = Money(14_300)))
            assertThat(paymentRepository.count()).isEqualTo(1)
            assertThat(paymentQueryService.payment(first.paymentId).lines).hasSize(2)

            then(invoiceQueryService).should(times(1)).invoice(INVOICE_ID)
        }

        @Test
        fun `결제 합계가 낼 돈에 도달하면 완료한다`() {
            paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))
            val result = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(10_000), Money(9_300))

            assertThat(result).isEqualTo(CashResult.Completed(result.paymentId))

            val view = paymentQueryService.payment(result.paymentId)
            assertThat(view.status).isEqualTo(PaymentStatus.COMPLETED)
            assertThat(view.completedAt).isNotNull() // 완료면 not null 이어야 하는 제약이 있으니까. (완료시각에 대한 증거)
            assertThat(view.paidAmount).isEqualTo(Money(29_300))
            assertThat(view.lines.map { it.change }).containsExactly(Money.ZERO, Money(700))
        }

        @Test
        fun `결제 합계가 낼 돈을 넘으면 거부하고 그 결제는 남지 않는다`() {
            val first = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))

            // 20,000 + 9,301 = 29,301 > 29,300 (경계에서 1원 넘음)
            val exception = assertThrows<PaymentAmountExceededException> {
                paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(10_000), Money(9_301))
            }

            assertThat(exception.paidAmountAfter).isEqualTo(Money(29_301))
            val view = paymentQueryService.payment(first.paymentId)
            assertThat(view.lines).hasSize(1)
            assertThat(view.paidAmount).isEqualTo(Money(20_000))
            assertThat(view.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @Test
        fun `완료 직전에 항목이 취소됐으면 완료하지 않고 받은 결제는 남긴다`() {
            // 이 슬라이스에서 가장 중요한 테스트다. 예외로 알렸다면 마지막 결제 기록이 롤백되어 사라진다(payment.md 7절).
            // 변이 확인: 서비스가 이 경우 예외를 던지도록 바꾸면 이 테스트가 실패해야 한다.
            val first = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))
            // 두 결제 사이에 의사가 혈액검사(502)를 취소했다.
            given(orderItemQueryService.statusesOf(listOf(501L, 502L, 503L))).willReturn(
                mapOf(501L to OrderItemStatus.EXECUTED, 502L to OrderItemStatus.CANCELLED, 503L to OrderItemStatus.ORDERED)
            )

            val result = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(10_000), Money(9_300))

            assertThat(result).isEqualTo(
                CashResult.CompletionBlocked(first.paymentId, ineffectiveItemIds = setOf(502L), alreadyPaidItemIds = emptySet())
            )
            val view = paymentQueryService.payment(first.paymentId)
            assertThat(view.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(view.completedAt).isNull()
            assertThat(view.lines).hasSize(2)   // 마지막 결제도 저장됐다
            assertThat(view.paidAmount).isEqualTo(Money(29_300))
        }

        @Test
        fun `같은 항목이 다른 수납 건으로 이미 완료됐으면 완료하지 않고 받은 결제는 남긴다`() {
            // T-PAY-03의 조건 "포함할 항목이 다른 완료된 수납 건에 없다"(INV-03)를 순서대로 부른 경우에 확인한다.
            // 동시에 완료를 시도하는 경우(C-03)는 이 확인으로 막히지 않는다. ADR-0003의 재현 대상이다.
            //
            // 산정 2는 수납 건 #1이 진행 중일 때 같은 항목(501~503)으로 만들어졌다(진행 중인 항목도 다시 산정할 수 있다).
            given(invoiceQueryService.invoice(SECOND_INVOICE_ID)).willReturn(scenarioInvoice().copy(id = SECOND_INVOICE_ID))
            val completedId = payFully()

            val result = paymentService.receiveCash(SECOND_INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(29_300), Money(29_300))

            assertThat(result.paymentId).isNotEqualTo(completedId)
            assertThat(result).isEqualTo(
                CashResult.CompletionBlocked(
                    result.paymentId, ineffectiveItemIds = emptySet(), alreadyPaidItemIds = setOf(501L, 502L, 503L),
                )
            )
            val blocked = paymentQueryService.payment(result.paymentId)
            assertThat(blocked.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(blocked.completedAt).isNull()
            assertThat(blocked.lines).hasSize(1)   // 받은 29,300원 기록은 남았다
            assertThat(paymentQueryService.payment(completedId).status).isEqualTo(PaymentStatus.COMPLETED)
        }

        @Test
        fun `완료된 산정으로 다시 결제하면 거부한다`() {
            // T-PAY-01의 조건 "해당 산정을 근거로 한 다른 수납 건이 없다"(A-22, INV-05)를 순서대로 부른 경우에 확인한다.
            // 변이 확인: 서비스의 existsByInvoiceId 줄을 지우면 이 테스트가 실패해야 한다.
            val completedId = payFully()

            // 1,000원은 확인이 없었다면 새 수납 건으로 아무 문제 없이 받아졌을 금액이다. 실패 원인을 "거부 안 함" 하나로 좁힌다.
            val exception = assertThrows<InvoiceAlreadyPaidException> {
                paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(1_000), Money(1_000))
            }

            assertThat(exception.invoiceId).isEqualTo(INVOICE_ID)
            assertThat(paymentRepository.count()).isEqualTo(1)
            val view = paymentQueryService.payment(completedId)
            assertThat(view.status).isEqualTo(PaymentStatus.COMPLETED)
            assertThat(view.lines).hasSize(2)
            // 산정을 조회하기 전에 거부했다. 산정 조회는 payFully의 첫 호출에서 한 번뿐이다.
            then(invoiceQueryService).should(times(1)).invoice(INVOICE_ID)
        }

        @Test
        fun `산정이 없으면 InvoiceNotFoundException이 발생하고 아무것도 남지 않는다`() {
            given(invoiceQueryService.invoice(UNKNOWN_ID)).willThrow(InvoiceNotFoundException(UNKNOWN_ID))

            val exception = assertThrows<InvoiceNotFoundException> {
                paymentService.receiveCash(UNKNOWN_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))
            }

            assertThat(exception.invoiceId).isEqualTo(UNKNOWN_ID)
            assertNoPaymentSaved()
        }

        @Test
        fun `창구가 없으면 CounterNotFoundException이 발생하고 아무것도 남지 않는다`() {
            given(counterQueryService.counter(UNKNOWN_ID)).willThrow(CounterNotFoundException(UNKNOWN_ID))

            val exception = assertThrows<CounterNotFoundException> {
                paymentService.receiveCash(INVOICE_ID, UNKNOWN_ID, CASHIER_NO, Money(20_000), Money(20_000))
            }

            assertThat(exception.counterId).isEqualTo(UNKNOWN_ID)
            assertNoPaymentSaved()
        }
    }

    @Nested
    inner class `수납 건 조회는` {

        @Test
        fun `없는 수납 건이면 PaymentNotFoundException이 발생한다`() {
            val exception = assertThrows<PaymentNotFoundException> { paymentQueryService.payment(UNKNOWN_ID) }

            assertThat(exception.paymentId).isEqualTo(UNKNOWN_ID)
        }
    }

    // ===== 도우미 =====

    /** 시나리오 산정을 두 번에 나눠 다 낸다(20,000 + 9,300). 완료된 수납 건 id를 돌려준다. */
    private fun payFully(): Long {
        paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(20_000), Money(20_000))
        return paymentService.receiveCash(INVOICE_ID, COUNTER_ID, CASHIER_NO, Money(10_000), Money(9_300)).paymentId
    }

    private fun item(id: Long, feeItemId: Long, quantity: Int, status: OrderItemStatus) =
        OrderItemView(id, orderId = 1, visitId = VISIT_ID, feeItemId = feeItemId, quantity = quantity,
            status = status, executableBeforePayment = false)

    private fun scenarioItems() = listOf(
        item(501, FEE_CONSULT, 1, OrderItemStatus.EXECUTED),
        item(502, FEE_BLOOD, 3, OrderItemStatus.ORDERED),
        item(503, FEE_CERT, 1, OrderItemStatus.ORDERED),
    )

    private fun scenarioTargets() = listOf(
        BillingTarget(501, FEE_CONSULT, 1),
        BillingTarget(502, FEE_BLOOD, 3),
        BillingTarget(503, FEE_CERT, 1),
    )

    /** billing 시나리오 2의 산정. payment가 쓰는 값은 id, 내원 건, 본인부담 합계, 내역의 오더 항목이다. */
    private fun scenarioInvoice() = InvoiceView(
        id = INVOICE_ID, visitId = VISIT_ID, eligibilitySeq = 1,
        insuranceType = InsuranceType.NATIONAL_HEALTH, copayRate = 30,
        coveredLineSum = Money(31_068), coveredTotal = Money(31_060), coveredCopay = Money(9_300),
        insurerAmount = Money(21_760), nonCoveredTotal = Money(20_000), patientAmount = Money(29_300),
        calculatedAt = LocalDateTime.of(2026, 6, 2, 11, 0),
        lines = listOf(
            InvoiceLineView(1, 501, FEE_CONSULT, CoverageType.COVERED, Money(17_619), 1, Money(17_619)),
            InvoiceLineView(2, 502, FEE_BLOOD, CoverageType.COVERED, Money(4_483), 3, Money(13_449)),
            InvoiceLineView(3, 503, FEE_CERT, CoverageType.NON_COVERED, Money(20_000), 1, Money(20_000)),
        ),
    )

    private fun assertNoPaymentSaved() {
        assertThat(paymentRepository.count()).isZero()
    }

    companion object {
        const val VISIT_ID = 1000L
        const val UNKNOWN_ID = 9999L
        val VISIT_DATE: LocalDate = LocalDate.of(2026, 6, 2)

        const val INVOICE_ID = 1L
        const val SECOND_INVOICE_ID = 2L
        const val COUNTER_ID = 7L
        const val CASHIER_NO = "T-C01"

        const val FEE_CONSULT = 11L
        const val FEE_BLOOD = 22L
        const val FEE_CERT = 33L
    }
}
