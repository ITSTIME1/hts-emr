package com.htsemr.billing

import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.reception.InsuranceType
import java.time.LocalDateTime

/**
 * 산정. billing 모듈 밖으로 내보내는 값 객체다.
 *
 * 금액의 뜻은 V6 마이그레이션의 컬럼 주석과 같다.
 * - 요양급여비용총액(coveredTotal) = 급여 항목 합(coveredLineSum)에서 10원 미만을 버린 값
 * - 본인일부부담금(coveredCopay) = 요양급여비용총액 × 본인부담률에서 100원 미만을 버린 값
 * - 공단부담금(insurerAmount) = 요양급여비용총액 − 본인일부부담금
 * - 본인부담 합계(patientAmount) = 본인일부부담금 + 비급여 합계. 수납 대상 금액이다.
 * 일반 자격은 끝전을 처리하지 않고 공단부담금이 0이다(A-31).
 */
data class InvoiceView(
    val id: Long,
    val visitId: Long,
    val eligibilitySeq: Int,
    val insuranceType: InsuranceType,
    val copayRate: Int,
    val coveredLineSum: Money,
    val coveredTotal: Money,
    val coveredCopay: Money,
    val insurerAmount: Money,
    val nonCoveredTotal: Money,
    val patientAmount: Money,
    val calculatedAt: LocalDateTime,
    val lines: List<InvoiceLineView>,
)

/** 산정 내역. 항목별 본인부담·공단부담은 나누지 않는다(A-32). */
data class InvoiceLineView(
    val id: Long,
    val orderItemId: Long,
    val feeItemId: Long,
    val coverageType: CoverageType,
    val unitPrice: Money,
    val quantity: Int,
    val lineTotal: Money,
)
