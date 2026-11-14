package com.htsemr.billing

import com.htsemr.TestcontainersConfiguration
import com.htsemr.billing.entity.Invoice
import com.htsemr.billing.entity.InvoiceLine
import com.htsemr.billing.repository.InvoiceLineRepository
import com.htsemr.billing.repository.InvoiceRepository
import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.master.FeeItemView
import com.htsemr.master.FeePriceNotFoundException
import com.htsemr.master.FeeQueryService
import com.htsemr.reception.EligibilityQueryService
import com.htsemr.reception.EligibilityView
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 산정(calculate)과 산정 조회(invoice) 테스트.
 *
 * 계산 규칙(끝전, 일반 자격)은 InvoiceTest가 DB 없이 확인한다. 여기서는 그 계산이
 * 다른 모듈의 조회 결과와 제대로 이어지고, 저장·조회·롤백·DB 제약이 기대대로 동작하는지 본다.
 *
 * 다른 모듈의 공개 조회(reception, master)는 대역으로 채운다.
 * 서비스가 자기 트랜잭션을 커밋하므로 클래스에 @Transactional을 붙이지 않고, 테스트마다 직접 지운다(OrderServiceTest와 같은 이유).
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class BillingServiceTest @Autowired constructor(
    private val billingService: BillingService,
    private val invoiceQueryService: InvoiceQueryService,
    private val invoiceRepository: InvoiceRepository,
    private val invoiceLineRepository: InvoiceLineRepository,
    private val jdbcTemplate: JdbcTemplate,
) {

    @MockitoBean
    private lateinit var visitQueryService: VisitQueryService

    @MockitoBean
    private lateinit var eligibilityQueryService: EligibilityQueryService

    @MockitoBean
    private lateinit var feeQueryService: FeeQueryService

    /**
     * 기본 응답: 내원 건 하나, 건강보험 30% 자격, 수가 항목 셋(시나리오 2의 단가).
     * 다른 응답이 필요한 테스트만 덮어쓴다.
     */
    @BeforeEach
    fun stubOtherModules() {
        given(visitQueryService.visit(VISIT_ID))
            .willReturn(VisitView(VISIT_ID, "T-P001", "T-IM", "T-D001", VISIT_DATE))
        given(eligibilityQueryService.currentEligibility(VISIT_ID))
            .willReturn(eligibility(InsuranceType.NATIONAL_HEALTH, copayRate = 30))

        stubFee(FEE_CONSULT, "진찰료", CoverageType.COVERED, 17_619)
        stubFee(FEE_BLOOD, "일반혈액검사", CoverageType.COVERED, 4_483)
        stubFee(FEE_CERT, "진단서", CoverageType.NON_COVERED, 20_000)
    }

    /** 외래 키 방향(내역 → 산정)의 반대 순서로 지운다. */
    @AfterEach
    fun cleanUp() {
        invoiceLineRepository.deleteAll()
        invoiceRepository.deleteAll()
    }

    @Nested
    inner class `산정을 만들면` {

        @Test
        fun `시나리오 2 - 법정 기준으로 계산한 합계를 저장하고 조회할 수 있다`() {

            fun scenarioTargets() = listOf(
                BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
                BillingTarget(orderItemId = ORDER_ITEM_3, feeItemId = FEE_CERT, quantity = 1),
            )

            val invoiceId : Long = billingService.calculate(visitId = VISIT_ID, targets = scenarioTargets())

            // when: calculate → invoice(id)

            val invoice : Invoice = invoiceRepository.findById(invoiceId).get()

            assertThat(invoice.coveredTotal.amount).isEqualTo(31_060)
            assertThat(invoice.coveredLineSum.amount).isEqualTo(31_068)
            assertThat(invoice.coveredCopay.amount).isEqualTo(9_300)
            assertThat(invoice.insurerAmount.amount).isEqualTo(21_760)
            assertThat(invoice.nonCoveredTotal.amount).isEqualTo(20_000)
            assertThat(invoice.patientAmount.amount).isEqualTo(29_300)
        }

        @Test
        fun `헤더의 급여 합과 비급여 합이 내역 총액의 합과 같다`() {
            fun scenarioTargets() = listOf(
                BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
                BillingTarget(orderItemId = ORDER_ITEM_3, feeItemId = FEE_CERT, quantity = 1),
            )

            val invoiceId : Long = billingService.calculate(visitId = VISIT_ID, targets = scenarioTargets())

            val invoice : Invoice = invoiceRepository.findById(invoiceId).get()

            val invoiceLine : List<InvoiceLine> = invoiceLineRepository.findAllByInvoiceIdOrderById(invoiceId)

            // Pair<List<CoveredInvoiceLine>, List<NonCoveredInvoiceLine>>
            val (coveredLines, nonCoveredLines) = invoiceLine.partition { it.coverageType == CoverageType.COVERED }

            // 급여합과 비급여합을 각각 합산한다.
            val coveredLineTotalAmounts = coveredLines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }
            val nonCoveredLineTotalAmounts = nonCoveredLines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }

            assertThat(invoice.coveredLineSum).isEqualTo(coveredLineTotalAmounts)
            assertThat(invoice.nonCoveredTotal).isEqualTo(nonCoveredLineTotalAmounts)
        }

        @Test
        fun `내역을 대상 순서대로 저장하고 단가와 총액을 값으로 남긴다`() {
            // then: lines의 orderItemId가 [ORDER_ITEM_1, ORDER_ITEM_2, ORDER_ITEM_3] 순서
            //       둘째 내역: unitPrice 4,483 / quantity 3 / lineTotal 13,449 / coverageType COVERED
            fun scenarioTargets() = listOf(
                BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
                BillingTarget(orderItemId = ORDER_ITEM_3, feeItemId = FEE_CERT, quantity = 1),
            )

            val invoiceId : Long = billingService.calculate(visitId = VISIT_ID, targets = scenarioTargets())
            val invoiceLine : List<InvoiceLine> = invoiceLineRepository.findAllByInvoiceIdOrderById(invoiceId)

            val targetIndex = 1
            val validationTargetInvoiceLine : InvoiceLine = invoiceLine[targetIndex]
            assertThat(validationTargetInvoiceLine.unitPrice.amount).isEqualTo(4_483)
            assertThat(validationTargetInvoiceLine.quantity).isEqualTo(3)
            assertThat(validationTargetInvoiceLine.coverageType).isEqualTo(CoverageType.COVERED)
        }

        @Test
        fun `계산에 쓴 자격을 산정에 복사한다`() {
            // given: 자격 seq 2로 덮어쓴다
            // then: visitId, eligibilitySeq 2, insuranceType, copayRate 30
            fun scenarioTargets() = listOf(
                BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
                BillingTarget(orderItemId = ORDER_ITEM_3, feeItemId = FEE_CERT, quantity = 1),
            )

            val firstInvoiceId : Long = billingService.calculate(visitId = VISIT_ID, targets = scenarioTargets())
            val firstInvoice : Invoice = invoiceRepository.findById(firstInvoiceId).get()

            assertThat(firstInvoice.eligibilitySeq).isEqualTo(1)

            // 자격을 변경해주고
            given(eligibilityQueryService.currentEligibility(VISIT_ID))
                .willReturn(eligibility(insuranceType = InsuranceType.NATIONAL_HEALTH, seq = 2, copayRate = 30))


            // 계산을 다시해서 mockitobean을 부른 다음 Eli..View를 리턴하면 그 값을 invoice에 저장해서, 다시 조회하면 자격이 변동된게 적용된 것을 알 수 있음.
            val secondInvoiceId : Long = billingService.calculate(visitId = VISIT_ID, targets = scenarioTargets())
            val secondInvoice : Invoice = invoiceRepository.findById(secondInvoiceId).get()

            assertThat(secondInvoice.eligibilitySeq).isEqualTo(2)
        }

        @Test
        fun `만든 뒤 수가나 자격이 바뀌어도 산정은 바뀌지 않는다`() {
            val invoiceId = billingService.calculate(VISIT_ID, scenarioTargets())
            val before = invoiceQueryService.invoice(invoiceId)

            // 산정 뒤 단가가 오르고(A-06의 새 적용 시작일), 자격이 새로 기록됐다(A-03)고 가정한다.
            given(feeQueryService.priceOn(FEE_CONSULT, VISIT_DATE)).willReturn(Money(20_000))
            given(eligibilityQueryService.currentEligibility(VISIT_ID))
                .willReturn(eligibility(InsuranceType.NATIONAL_HEALTH, copayRate = 60, seq = 2))

            val after = invoiceQueryService.invoice(invoiceId)

            // data class라 모든 필드(내역 포함)를 한 번에 비교한다.
            assertThat(after).isEqualTo(before)
            assertThat(after.eligibilitySeq).isEqualTo(1)
            assertThat(after.lines.first().unitPrice).isEqualTo(Money(17_619))
        }

        @Test
        fun `시나리오 3 - 일반 자격이면 끝전 없이 전액 본인부담이다`() {
            given(eligibilityQueryService.currentEligibility(VISIT_ID))
                .willReturn(eligibility(InsuranceType.UNINSURED, copayRate = 100))

            val invoice = invoiceQueryService.invoice(billingService.calculate(VISIT_ID, scenarioTargets()))

            assertThat(invoice.insuranceType).isEqualTo(InsuranceType.UNINSURED)
            assertThat(invoice.coveredLineSum).isEqualTo(Money(31_068))
            assertThat(invoice.coveredTotal).isEqualTo(Money(31_068))     // 10원 미만을 버리지 않는다(A-31)
            assertThat(invoice.coveredCopay).isEqualTo(Money(31_068))
            assertThat(invoice.insurerAmount).isEqualTo(Money.ZERO)
            assertThat(invoice.nonCoveredTotal).isEqualTo(Money(20_000))
            assertThat(invoice.patientAmount).isEqualTo(Money(51_068))
        }
    }

    @Nested
    inner class `산정을 거부하면 아무것도 남지 않는다` {


        @Test
        fun `내원 건이 없을 때`() {
            given(visitQueryService.visit(UNKNOWN_ID)).willThrow(VisitNotFoundException(UNKNOWN_ID))

            val exception = assertThrows<VisitNotFoundException> {
                billingService.calculate(UNKNOWN_ID, scenarioTargets())
            }

            assertThat(exception.visitId).isEqualTo(UNKNOWN_ID)
            assertNothingSaved()
        }

        @Test
        fun `수가 항목이 없을 때`() {
            given(feeQueryService.feeItem(feeItemId = UNKNOWN_ID)).willThrow(FeeItemNotFoundException(UNKNOWN_ID))

            assertThrows<FeeItemNotFoundException> {
                billingService.calculate(VISIT_ID, exceptionScenarioTargets())
            }
        }

        @Test
        fun `진료일에 유효한 단가가 없을 때`() {
            // given: 셋째 대상의 priceOn이 FeePriceNotFoundException을 던지게 한다
            //        앞의 두 대상은 조회에 성공한 뒤라도 아무것도 저장되지 않아야 한다
            given(feeQueryService.priceOn(feeItemId = FEE_CERT, visitDate = VISIT_DATE)).willThrow(FeePriceNotFoundException(feeItemId = FEE_CERT, visitDate = VISIT_DATE))

            val exception = assertThrows<FeePriceNotFoundException> {
                billingService.calculate(VISIT_ID, scenarioTargets())
            }

            // 예외가 반환한 수가id가 given()에서 설정한 수가id와 동일해야한다.
            assertThat(exception.feeItemId).isEqualTo(FEE_CERT)
            assertNothingSaved()
        }

        @Test
        fun `같은 오더 항목이 두 번 들어 있을 때`() {
            fun scenarioTargets() = listOf(
                BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
            )

            val exception = assertThrows<IllegalArgumentException> {
                billingService.calculate(VISIT_ID, scenarioTargets())
            }

            assertThat(exception.message).isEqualTo("같은 오더 항목이 두 번 들어 있습니다")
            assertNothingSaved()
        }

        @Test
        fun `수량이 1보다 작을 때`() {
            fun scenarioTargets() = listOf(
                BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
                BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 0), // 수량은 1이상이어야 하지만, 1미만으로 설정
            )

            val exception = assertThrows<IllegalArgumentException> {
                billingService.calculate(VISIT_ID, scenarioTargets())
            }

            assertThat(exception.message).isEqualTo("수량은 1 이상이어야 합니다: 0")
            assertNothingSaved()
        }

    }

    @Nested
    inner class `산정 조회는` {

        @Test
        fun `없는 산정이면 InvoiceNotFoundException이 발생한다`() {
            val exception = assertThrows<InvoiceNotFoundException> {
                invoiceQueryService.invoice(UNKNOWN_ID)
            }
            assertThat(exception.invoiceId).isEqualTo(UNKNOWN_ID)
        }
    }

    @Nested
    inner class `DB 체크 제약은` {
        private fun insertInvoice(
            insuranceType: String = "NATIONAL_HEALTH",
            copayRate: Int = 30,
            coveredLineSum: Long = 31_068,
            coveredTotal: Long = 31_060,
            coveredCopay: Long = 9_300,
            insurerAmount: Long = 21_760,
            nonCoveredTotal: Long = 20_000,
            patientAmount: Long = 29_300,
        ) {
            jdbcTemplate.update(
                """
                    insert into invoice (visit_id, eligibility_seq, insurance_type, copay_rate,
                                         covered_line_sum, covered_total, covered_copay, insurer_amount,
                                         non_covered_total, patient_amount, calculated_at)
                    values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, systimestamp)
                    """.trimIndent(),
                VISIT_ID, insuranceType, copayRate,
                coveredLineSum, coveredTotal, coveredCopay, insurerAmount,
                nonCoveredTotal, patientAmount,
            )
        }

        @Test
        fun `공단부담이 총액에서 본인부담을 뺀 값이 아니면 거부한다`() {
            // 31,060 − 9,300 = 21,760 이어야 하는데 1원 많다.
            val exception = assertThrows<DataIntegrityViolationException> {
                insertInvoice(insurerAmount = 21_761)
            }

            assertThat(exception.message).containsIgnoringCase("CK_INVOICE_INSURER")
        }

        @Test
        fun `일반 자격인데 공단부담이 있으면 거부한다`() {
            // ck_invoice_uninsured
            // 일반 자격은 공단부담이 0이어야 한다(A-31). 다른 제약은 모두 통과시키고 공단부담만 68원으로 둔다.
            val exception = assertThrows<DataIntegrityViolationException> {
                insertInvoice(
                    insuranceType = "UNINSURED",
                    copayRate = 100,
                    coveredLineSum = 31_068,
                    coveredTotal = 31_068,
                    coveredCopay = 31_000,
                    insurerAmount = 68, // 공단 부담이 없어야 됨
                    nonCoveredTotal = 20_000,
                    patientAmount = 51_000,
                )
            }

            assertThat(exception.message).containsIgnoringCase("CK_INVOICE_UNINSURED")
        }

        @Test
        fun `내역 총액이 단가 곱하기 수량이 아니면 거부한다`() {
            val invoiceId = billingService.calculate(VISIT_ID, scenarioTargets())

            // 4,483 × 3 = 13,449 이어야 하는데 1원 많다. 다른 제약(단가 ≥ 0, 수량 ≥ 1, 급여 구분, 산정 안 중복 없음)은 통과
            val exception = assertThrows<DataIntegrityViolationException> {
                jdbcTemplate.update(
                    """
            insert into invoice_line (invoice_id, order_item_id, fee_item_id, coverage_type, unit_price, quantity, line_total)
            values (?, ?, ?, 'COVERED', 4483, 3, 13450)
            """.trimIndent(),
                    invoiceId, 999L, FEE_BLOOD,
                )
            }

            assertThat(exception.message).containsIgnoringCase("CK_INVOICE_LINE_TOTAL")
        }
    }

    private fun stubFee(feeItemId: Long, name: String, coverageType: CoverageType, price: Long) {
        given(feeQueryService.feeItem(feeItemId))
            .willReturn(FeeItemView(feeItemId, "T-$feeItemId", name, coverageType, executableBeforePayment = false))
        given(feeQueryService.priceOn(feeItemId, VISIT_DATE)).willReturn(Money(price))
    }

    private fun eligibility(insuranceType: InsuranceType, copayRate: Int, seq: Int = 1) =
        EligibilityView(VISIT_ID, seq, insuranceType, copayRate, LocalDateTime.of(2026, 6, 2, 9, 0))

    /** 시나리오 2의 대상: 진찰료 1, 혈액검사 3, 진단서 1. */
    private fun scenarioTargets() = listOf(
        BillingTarget(orderItemId = ORDER_ITEM_1, feeItemId = FEE_CONSULT, quantity = 1),
        BillingTarget(orderItemId = ORDER_ITEM_2, feeItemId = FEE_BLOOD, quantity = 3),
        BillingTarget(orderItemId = ORDER_ITEM_3, feeItemId = FEE_CERT, quantity = 1),
    )
    private fun exceptionScenarioTargets() = listOf(BillingTarget(orderItemId = ORDER_ITEM_3, feeItemId = UNKNOWN_ID, quantity = 1))

    private fun assertNothingSaved() {
        assertThat(invoiceRepository.count()).isZero()
        assertThat(invoiceLineRepository.count()).isZero()
    }

    companion object {
        const val VISIT_ID = 1000L
        const val UNKNOWN_ID = 9999L
        val VISIT_DATE: LocalDate = LocalDate.of(2026, 6, 2)

        const val FEE_CONSULT = 11L
        const val FEE_BLOOD = 22L
        const val FEE_CERT = 33L

        const val ORDER_ITEM_1 = 501L
        const val ORDER_ITEM_2 = 502L
        const val ORDER_ITEM_3 = 503L
    }
}
