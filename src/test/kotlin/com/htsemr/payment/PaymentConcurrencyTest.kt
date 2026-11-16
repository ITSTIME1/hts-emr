package com.htsemr.payment

import com.htsemr.TestcontainersConfiguration
import com.htsemr.billing.BillingService
import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.billing.InvoiceView
import com.htsemr.common.Money
import com.htsemr.master.CounterQueryService
import com.htsemr.master.CounterView
import com.htsemr.master.CoverageType
import com.htsemr.order.OrderItemQueryService
import com.htsemr.order.OrderItemStatus
import com.htsemr.payment.repository.PaymentRepository
import com.htsemr.reception.InsuranceType
import com.htsemr.reception.VisitQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class PaymentConcurrencyTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @MockitoBean private lateinit var visitQueryService: VisitQueryService
    @MockitoBean private lateinit var orderItemQueryService: OrderItemQueryService
    @MockitoBean private lateinit var billingService: BillingService
    @MockitoBean private lateinit var invoiceQueryService: InvoiceQueryService
    @MockitoBean private lateinit var counterQueryService: CounterQueryService

    @BeforeEach
    fun stubOtherModules() {
        given(counterQueryService.counter(COUNTER_ID)).willReturn(CounterView(COUNTER_ID, "T-C1", "1번 창구"))
    }

    @AfterEach
    fun cleanUp() {
        transactionTemplate.executeWithoutResult { paymentRepository.deleteAll() }
    }

    @Test
    fun `같은 산정으로 동시에 첫 결제를 받아도 수납 건은 하나이고 성공한 결제는 모두 남는다`() {

        val arrived = CountDownLatch(2)
        given(invoiceQueryService.invoice(INVOICE_ID)).willAnswer {
            arrived.countDown()
            arrived.await(2, TimeUnit.SECONDS)
            scenarioInvoice()
        }

        val pool = Executors.newFixedThreadPool(2)

        val futureA = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", PAY, PAY) }
        }

        val futureB = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C02", PAY, PAY) }
        }

        val resultA = futureA.get(10, TimeUnit.SECONDS)
        val resultB = futureB.get(10, TimeUnit.SECONDS)
        pool.shutdown()

        assertThat(paymentRepository.count())
            .`as`("산정 %d의 수납 건 수", INVOICE_ID)
            .isEqualTo(1)

        val succeeded = listOf(resultA, resultB).count { it.isSuccess }
        val storedPaidTotal = transactionTemplate.execute {
            paymentRepository.findAll().fold(Money.ZERO) { sum, payment -> sum + payment.paidAmount }
        }
        assertThat(storedPaidTotal)
            .`as`("성공 응답 %d건의 결제가 모두 저장됐는지", succeeded)
            .isEqualTo(PAY * succeeded)
    }

    @Test
    fun `동시에 첫 결제가 겹쳐 진 창구는 다시 시도하라는 예외를 받고, 다시 시도하면 먼저 만든 수납 건에 이어서 받는다`() {
        val arrived = CountDownLatch(2)
        given(invoiceQueryService.invoice(INVOICE_ID)).willAnswer {
            arrived.countDown()
            arrived.await(2, TimeUnit.SECONDS)
            scenarioInvoice()
        }

        val pool = Executors.newFixedThreadPool(2)
        val futureA = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", PAY, PAY) }
        }
        val futureB = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C02", PAY, PAY) }
        }
        val resultA = futureA.get(10, TimeUnit.SECONDS)
        val resultB = futureB.get(10, TimeUnit.SECONDS)
        pool.shutdown()

        val lost = listOf(resultA, resultB).single { it.isFailure }
        assertThat(lost.exceptionOrNull()).isInstanceOf(PaymentStartConflictException::class.java)

        val retried = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C02", PAY, PAY)

        assertThat(retried).isInstanceOf(CashResult.InProgress::class.java)
        assertThat((retried as CashResult.InProgress).remaining).isEqualTo(Money(9_300))
        assertThat(paymentRepository.count()).isEqualTo(1)
    }

    @Test
    fun `같은 항목을 가진 두 수납 건이 동시에 완료해도 완료되는 수납 건은 하나이고 성공한 결제는 모두 남는다`() {
        given(invoiceQueryService.invoice(INVOICE_ID)).willReturn(scenarioInvoice(INVOICE_ID))
        given(invoiceQueryService.invoice(OTHER_INVOICE_ID)).willReturn(scenarioInvoice(OTHER_INVOICE_ID))

        paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", Money(20_000), Money(20_000))
        paymentService.receiveCash(OTHER_INVOICE_ID, COUNTER_ID, "T-C02", Money(20_000), Money(20_000))

        val arrived = CountDownLatch(2)
        given(orderItemQueryService.statusesOf(anyCollection())).willAnswer {
            arrived.countDown()
            arrived.await(2, TimeUnit.SECONDS)
            ALL_EFFECTIVE
        }

        val pool = Executors.newFixedThreadPool(2)
        val futureA = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", LAST_PAY, LAST_PAY) }
        }
        val futureB = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(OTHER_INVOICE_ID, COUNTER_ID, "T-C02", LAST_PAY, LAST_PAY) }
        }
        val resultA = futureA.get(10, TimeUnit.SECONDS)
        val resultB = futureB.get(10, TimeUnit.SECONDS)
        pool.shutdown()

        val completedWithItem = transactionTemplate.execute {
            paymentRepository.findAll().count { it.status == PaymentStatus.COMPLETED && 501L in it.orderItemIds }
        }
        assertThat(completedWithItem).`as`("항목 501을 가진 완료된 수납 건 수").isLessThanOrEqualTo(1)

        val succeeded = listOf(resultA, resultB).count { it.isSuccess }
        val storedPaidTotal = transactionTemplate.execute {
            paymentRepository.findAll().fold(Money.ZERO) { sum, payment -> sum + payment.paidAmount }
        }
        assertThat(storedPaidTotal)
            .`as`("성공 응답 %d건의 결제가 모두 저장됐는지", succeeded)
            .isEqualTo(Money(40_000) + LAST_PAY * succeeded)
    }

    @Test
    fun `같은 항목을 가진 두 새 수납 건이 한 번에 다 내며 동시에 완료해도 완료되는 수납 건은 하나다`() {
        given(invoiceQueryService.invoice(INVOICE_ID)).willReturn(scenarioInvoice(INVOICE_ID))
        given(invoiceQueryService.invoice(OTHER_INVOICE_ID)).willReturn(scenarioInvoice(OTHER_INVOICE_ID))

        val arrived = CountDownLatch(2)
        given(orderItemQueryService.statusesOf(anyCollection())).willAnswer {
            arrived.countDown()
            arrived.await(2, TimeUnit.SECONDS)
            ALL_EFFECTIVE
        }

        val pool = Executors.newFixedThreadPool(2)
        val futureA = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", FULL_PAY, FULL_PAY) }
        }
        val futureB = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(OTHER_INVOICE_ID, COUNTER_ID, "T-C02", FULL_PAY, FULL_PAY) }
        }
        val resultA = futureA.get(10, TimeUnit.SECONDS)
        val resultB = futureB.get(10, TimeUnit.SECONDS)
        pool.shutdown()

        val completedWithItem = transactionTemplate.execute {
            paymentRepository.findAll().count { it.status == PaymentStatus.COMPLETED && 501L in it.orderItemIds }
        }
        assertThat(completedWithItem).`as`("항목 501을 가진 완료된 수납 건 수").isLessThanOrEqualTo(1)

        val succeeded = listOf(resultA, resultB).count { it.isSuccess }
        val storedPaidTotal = transactionTemplate.execute {
            paymentRepository.findAll().fold(Money.ZERO) { sum, payment -> sum + payment.paidAmount }
        }
        assertThat(storedPaidTotal)
            .`as`("성공 응답 %d건의 결제가 모두 저장됐는지", succeeded)
            .isEqualTo(FULL_PAY * succeeded)
    }

    @Test
    fun `같은 항목의 완료가 겹쳐 진 창구는 결제가 저장되지 않았다는 예외를 받고, 다시 시도하면 완료가 막힌 결과를 받는다`() {
        given(invoiceQueryService.invoice(INVOICE_ID)).willReturn(scenarioInvoice(INVOICE_ID))
        given(invoiceQueryService.invoice(OTHER_INVOICE_ID)).willReturn(scenarioInvoice(OTHER_INVOICE_ID))
        paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", Money(20_000), Money(20_000))
        paymentService.receiveCash(OTHER_INVOICE_ID, COUNTER_ID, "T-C02", Money(20_000), Money(20_000))

        val arrived = CountDownLatch(2)
        given(orderItemQueryService.statusesOf(anyCollection())).willAnswer {
            arrived.countDown()
            arrived.await(2, TimeUnit.SECONDS)
            ALL_EFFECTIVE
        }

        val pool = Executors.newFixedThreadPool(2)
        val futureA = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", LAST_PAY, LAST_PAY) }
        }
        val futureB = pool.submit<Result<CashResult>> {
            runCatching { paymentService.receiveCash(OTHER_INVOICE_ID, COUNTER_ID, "T-C02", LAST_PAY, LAST_PAY) }
        }
        val results = mapOf(INVOICE_ID to futureA.get(10, TimeUnit.SECONDS), OTHER_INVOICE_ID to futureB.get(10, TimeUnit.SECONDS))
        pool.shutdown()

        val (lostInvoiceId, lost) = results.entries.single { it.value.isFailure }
        assertThat(lost.exceptionOrNull()).isInstanceOf(PaymentCompletionConflictException::class.java)

        val retried = paymentService.receiveCash(lostInvoiceId, COUNTER_ID, "T-C03", LAST_PAY, LAST_PAY)

        assertThat(retried).isInstanceOf(CashResult.CompletionBlocked::class.java)
        assertThat((retried as CashResult.CompletionBlocked).alreadyPaidItemIds).containsExactlyInAnyOrder(501L, 502L, 503L)
        val completedWithItem = transactionTemplate.execute {
            paymentRepository.findAll().count { it.status == PaymentStatus.COMPLETED && 501L in it.orderItemIds }
        }
        assertThat(completedWithItem).isEqualTo(1)
    }

    private fun scenarioInvoice(id: Long = INVOICE_ID) = InvoiceView(
        id = id, visitId = VISIT_ID, eligibilitySeq = 1,
        insuranceType = InsuranceType.NATIONAL_HEALTH, copayRate = 30,
        coveredLineSum = Money(31_068), coveredTotal = Money(31_060), coveredCopay = Money(9_300),
        insurerAmount = Money(21_760), nonCoveredTotal = Money(20_000), patientAmount = Money(29_300),
        calculatedAt = LocalDateTime.of(2026, 6, 2, 11, 0),
        lines = listOf(
            InvoiceLineView(1, 501, 11, CoverageType.COVERED, Money(17_619), 1, Money(17_619)),
            InvoiceLineView(2, 502, 22, CoverageType.COVERED, Money(4_483), 3, Money(13_449)),
            InvoiceLineView(3, 503, 33, CoverageType.NON_COVERED, Money(20_000), 1, Money(20_000)),
        ),
    )

    companion object {
        const val VISIT_ID = 1000L
        const val INVOICE_ID = 1L
        const val COUNTER_ID = 7L
        val PAY = Money(10_000)

        const val OTHER_INVOICE_ID = 2L
        val LAST_PAY = Money(9_300)
        val FULL_PAY = Money(29_300)
        val ALL_EFFECTIVE = mapOf(
            501L to OrderItemStatus.EXECUTED,
            502L to OrderItemStatus.ORDERED,
            503L to OrderItemStatus.ORDERED,
        )
    }
}
