package com.htsemr.payment

import com.htsemr.TestcontainersConfiguration
import com.htsemr.billing.BillingService
import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.billing.InvoiceView
import com.htsemr.common.Money
import com.htsemr.master.CounterQueryService
import com.htsemr.master.CoverageType
import com.htsemr.order.OrderItemQueryService
import com.htsemr.order.OrderItemStatus
import com.htsemr.payment.entity.Payment
import com.htsemr.payment.mapping.toPaymentView
import com.htsemr.payment.repository.PaymentRepository
import com.htsemr.reception.InsuranceType
import com.htsemr.reception.VisitQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * 수납 건의 저장 매핑 테스트. 서비스 없이 리포지토리로 저장하고 다시 읽어, 결제와 항목이 함께 저장되는지(cascade)와
 * 리포지토리 조회가 기대대로 동작하는지 본다.
 *
 * 각 단계를 별도 트랜잭션으로 실행한다. 한 트랜잭션 안에서 다시 읽으면 DB가 아니라 영속성 컨텍스트에서 같은 객체가 나와,
 * 저장이 실제로 됐는지 확인할 수 없다.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class PaymentMappingTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    // @ApplicationModuleTest는 payment 모듈만 띄운다. 서비스 구현체가 주입받는 다른 모듈의 공개 기능을 대역으로 채운다.
    // 이 테스트는 리포지토리만 쓰므로 대역의 응답은 설정하지 않는다.
    @MockitoBean private lateinit var visitQueryService: VisitQueryService
    @MockitoBean private lateinit var orderItemQueryService: OrderItemQueryService
    @MockitoBean private lateinit var billingService: BillingService
    @MockitoBean private lateinit var invoiceQueryService: InvoiceQueryService
    @MockitoBean private lateinit var counterQueryService: CounterQueryService

    @AfterEach
    fun cleanUp() {
        // 결제와 항목은 수납 건의 cascade로 함께 지워진다.
        transactionTemplate.executeWithoutResult { paymentRepository.deleteAll() }
    }

    @Test
    fun `수납 건을 저장하면 결제와 포함 항목도 함께 저장되고 다시 읽을 수 있다`() {
        val paymentId = transactionTemplate.execute {
            val payment = Payment.start(invoice(), counterId = 7, cashierNo = "T-C01", startedAt = NOW)
            payment.receiveCash(Money(20_000), Money(20_000), NOW)
            payment.receiveCash(Money(10_000), Money(9_300), NOW)
            requireNotNull(paymentRepository.save(payment).id)
        }!!

        val view = transactionTemplate.execute { paymentRepository.findById(paymentId).get().toPaymentView() }!!

        assertThat(view.orderItemIds).containsExactly(501L, 502L, 503L)
        assertThat(view.lines.map { it.amount }).containsExactly(Money(20_000), Money(9_300))
        assertThat(view.lines.map { it.change }).containsExactly(Money.ZERO, Money(700))
        assertThat(view.paidAmount).isEqualTo(Money(29_300))
        assertThat(view.status).isEqualTo(PaymentStatus.IN_PROGRESS)
    }

    @Test
    fun `완료 상태와 완료 시각이 저장된다`() {
        val paymentId = transactionTemplate.execute {
            val payment = Payment.start(invoice(), counterId = 7, cashierNo = "T-C01", startedAt = NOW)
            payment.receiveCash(Money(29_300), Money(29_300), NOW)
            payment.complete(ALL_EFFECTIVE, emptySet(), COMPLETED_AT)
            requireNotNull(paymentRepository.save(payment).id)
        }!!

        val view = transactionTemplate.execute { paymentRepository.findById(paymentId).get().toPaymentView() }!!

        assertThat(view.status).isEqualTo(PaymentStatus.COMPLETED)
        assertThat(view.completedAt).isEqualTo(COMPLETED_AT)
    }

    @Test
    fun `산정과 상태로 수납 건을 찾는다`() {
        transactionTemplate.executeWithoutResult {
            paymentRepository.save(Payment.start(invoice(), counterId = 7, cashierNo = "T-C01", startedAt = NOW))
        }

        assertThat(paymentRepository.findByInvoiceIdAndStatus(INVOICE_ID, PaymentStatus.IN_PROGRESS)).isNotNull()
        assertThat(paymentRepository.findByInvoiceIdAndStatus(INVOICE_ID, PaymentStatus.COMPLETED)).isNull()
        assertThat(paymentRepository.findByInvoiceIdAndStatus(9999L, PaymentStatus.IN_PROGRESS)).isNull()
    }

    @Test
    fun `완료된 수납 건에 들어 있는 오더 항목만 고른다`() {
        transactionTemplate.executeWithoutResult {
            // 완료된 수납 건(항목 501~503)과, 진행 중인 수납 건(같은 항목) 하나씩
            // 한 산정에 수납 건은 하나라(INV-05), 진행 중인 수납 건은 같은 항목을 다시 산정한 다른 산정으로 만든다.
            val completed = Payment.start(invoice(), counterId = 7, cashierNo = "T-C01", startedAt = NOW)
            completed.receiveCash(Money(29_300), Money(29_300), NOW)
            completed.complete(ALL_EFFECTIVE, emptySet(), COMPLETED_AT)
            paymentRepository.save(completed)
            paymentRepository.save(Payment.start(invoice(id = 2), counterId = 7, cashierNo = "T-C02", startedAt = NOW))
        }

        val paid = paymentRepository.findOrderItemIdsIn(listOf(501L, 503L, 999L), PaymentStatus.COMPLETED)

        assertThat(paid).containsExactlyInAnyOrder(501L, 503L)
    }

    private fun invoice(id: Long = INVOICE_ID) = InvoiceView(
        id = id, visitId = 1000, eligibilitySeq = 1,
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
        val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 2, 11, 30)
        val COMPLETED_AT: LocalDateTime = LocalDateTime.of(2026, 6, 2, 11, 35)
        val ALL_EFFECTIVE = mapOf(
            501L to OrderItemStatus.EXECUTED,
            502L to OrderItemStatus.ORDERED,
            503L to OrderItemStatus.ORDERED,
        )
    }
}
