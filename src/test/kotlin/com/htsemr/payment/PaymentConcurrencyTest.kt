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
import com.htsemr.payment.repository.PaymentRepository
import com.htsemr.reception.InsuranceType
import com.htsemr.reception.VisitQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

    private fun scenarioInvoice() = InvoiceView(
        id = INVOICE_ID, visitId = VISIT_ID, eligibilitySeq = 1,
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
    }
}
