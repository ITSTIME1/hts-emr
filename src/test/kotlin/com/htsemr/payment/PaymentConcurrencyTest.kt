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

    // 외부 공개 기능은 mocking 주입
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
            arrived.countDown()                        // 나 도착 (2 → 1, 또는 1 → 0)
            arrived.await(2, TimeUnit.SECONDS)          // 상대를 기다림. 2초 안에 안 오면(잠금 방어) 혼자 진행
            scenarioInvoice()                           // 그다음 산정을 돌려준다
        }

        val pool = Executors.newFixedThreadPool(2)

        // runCatching은 = try-catch역할을 함. 메소드 체이닝까지 지원.
        // runCatching 블럭 내부에서 receiveCash를 호출하는 작업을 정의
        // 예외 및 실패에 대해서 어떻게 처리할지는 구체적으로 정하지 않음.
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
        // 결제는 지연 로딩이라 트랜잭션 안에서 꺼내 더한다.
        val storedPaidTotal = transactionTemplate.execute {
            paymentRepository.findAll().fold(Money.ZERO) { sum, payment -> sum + payment.paidAmount }
        }
        assertThat(storedPaidTotal)
            .`as`("성공 응답 %d건의 결제가 모두 저장됐는지", succeeded)
            .isEqualTo(PAY * succeeded)
    }

    /**
     * C-06에서 진 창구가 받는 응답. 유일 제약(V9)을 고른 뒤의 계약이라, 위 테스트와 달리 예외 종류를 본다.
     * 진 창구는 "다시 시도하라"는 예외를 받고, 다시 시도하면 먼저 만든 수납 건에 이어서 받는다.
     */
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

        // 둘 중 정확히 하나가 졌고, 그 창구는 다시 시도하라는 예외를 받았다
        val lost = listOf(resultA, resultB).single { it.isFailure }
        assertThat(lost.exceptionOrNull()).isInstanceOf(PaymentStartConflictException::class.java)

        // 진 창구가 같은 결제를 다시 시도한다. 이번에는 진행 중인 수납 건이 보이므로 거기에 이어서 받는다
        val retried = paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C02", PAY, PAY)

        assertThat(retried).isInstanceOf(CashResult.InProgress::class.java)
        assertThat((retried as CashResult.InProgress).remaining).isEqualTo(Money(9_300))   // 29,300 − 10,000 × 2
        assertThat(paymentRepository.count()).isEqualTo(1)
    }

    /**
     * C-03. 같은 항목을 가진 두 수납 건이 같은 순간에 완료한다(INV-03).
     *
     * 같은 항목을 다시 산정할 수 있으므로(진행 중인 항목도 다시 고를 수 있다), 산정 1과 산정 2가 항목 501~503을 함께 가진다.
     * 두 수납 건 모두 20,000원까지 받아 둔 상태에서, 두 창구가 남은 9,300원을 같은 순간에 받는다.
     *
     * 틈: findOrderItemIdsIn(다른 완료된 수납 건 확인) → 커밋. 두 스레드는 그 직전의 statusesOf에서 만난다.
     * 방어가 없으면 둘 다 "다른 완료된 수납 건 없음"을 보고 둘 다 완료한다.
     */
    @Test
    fun `같은 항목을 가진 두 수납 건이 동시에 완료해도 완료되는 수납 건은 하나이고 성공한 결제는 모두 남는다`() {
        given(invoiceQueryService.invoice(INVOICE_ID)).willReturn(scenarioInvoice(INVOICE_ID))
        given(invoiceQueryService.invoice(OTHER_INVOICE_ID)).willReturn(scenarioInvoice(OTHER_INVOICE_ID))

        // 준비: 두 수납 건을 순서대로 만들고 20,000원씩 받아 둔다. 아직 덜 냈으므로 완료 단계에 가지 않는다
        paymentService.receiveCash(INVOICE_ID, COUNTER_ID, "T-C01", Money(20_000), Money(20_000))
        paymentService.receiveCash(OTHER_INVOICE_ID, COUNTER_ID, "T-C02", Money(20_000), Money(20_000))

        // 완료 단계의 첫 재료인 항목 상태 조회에서 서로를 기다린다
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

        // 규칙: 항목 501을 가진 완료된 수납 건은 하나뿐이다(INV-03)
        val completedWithItem = transactionTemplate.execute {
            paymentRepository.findAll().count { it.status == PaymentStatus.COMPLETED && 501L in it.orderItemIds }
        }
        assertThat(completedWithItem).`as`("항목 501을 가진 완료된 수납 건 수").isLessThanOrEqualTo(1)

        // 응답 = 기록: 준비에서 받은 40,000원에, 성공 응답을 받은 마지막 결제가 모두 더해져 있다
        val succeeded = listOf(resultA, resultB).count { it.isSuccess }
        val storedPaidTotal = transactionTemplate.execute {
            paymentRepository.findAll().fold(Money.ZERO) { sum, payment -> sum + payment.paidAmount }
        }
        assertThat(storedPaidTotal)
            .`as`("성공 응답 %d건의 결제가 모두 저장됐는지", succeeded)
            .isEqualTo(Money(40_000) + LAST_PAY * succeeded)
    }

    /**
     * C-03의 다른 모양. 두 수납 건이 모두 이 호출에서 새로 생기고, 한 번에 다 내며 같은 순간에 완료한다.
     *
     * 위 테스트와 다른 점: 두 수납 건 모두 아직 커밋되지 않은 새 행이라, 서로의 행이 보이지 않는다.
     * 그래서 "이미 있는 수납 건 행을 잠근다"는 방어로는 막을 수 없다. 방어 방법을 고르는 근거가 되는 경우다(ADR-0003).
     */
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

    /**
     * C-03에서 진 창구가 받는 응답. 완료된 항목의 유일 인덱스(V10)를 고른 뒤의 계약이라 예외 종류를 본다.
     * 진 창구의 마지막 결제는 저장되지 않고, 다시 시도하면 순서대로 왔을 때와 같이 완료가 막힌 결과를 받는다.
     */
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

        // 둘 중 정확히 하나가 졌고, 그 창구는 결제가 저장되지 않았다는 예외를 받았다
        val (lostInvoiceId, lost) = results.entries.single { it.value.isFailure }
        assertThat(lost.exceptionOrNull()).isInstanceOf(PaymentCompletionConflictException::class.java)

        // 진 창구가 같은 결제를 다시 시도한다. 이번에는 먼저 완료된 수납 건이 보이므로, 결제는 남고 완료가 막힌다
        val retried = paymentService.receiveCash(lostInvoiceId, COUNTER_ID, "T-C03", LAST_PAY, LAST_PAY)

        assertThat(retried).isInstanceOf(CashResult.CompletionBlocked::class.java)
        assertThat((retried as CashResult.CompletionBlocked).alreadyPaidItemIds).containsExactlyInAnyOrder(501L, 502L, 503L)
        val completedWithItem = transactionTemplate.execute {
            paymentRepository.findAll().count { it.status == PaymentStatus.COMPLETED && 501L in it.orderItemIds }
        }
        assertThat(completedWithItem).isEqualTo(1)
    }

    /** billing 시나리오 2의 산정(낼 돈 29,300원). 테스트 데이터라 여기에 둔다. C-03은 같은 항목의 산정 둘을 쓴다. */
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

        // C-03
        const val OTHER_INVOICE_ID = 2L
        val LAST_PAY = Money(9_300)      // 20,000원을 받아 둔 수납 건의 남은 금액
        val FULL_PAY = Money(29_300)     // 한 번에 다 내는 금액
        val ALL_EFFECTIVE = mapOf(
            501L to OrderItemStatus.EXECUTED,
            502L to OrderItemStatus.ORDERED,
            503L to OrderItemStatus.ORDERED,
        )
    }
}
