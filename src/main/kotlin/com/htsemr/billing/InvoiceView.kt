package com.htsemr.billing

import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.reception.InsuranceType
import java.time.LocalDateTime

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

data class InvoiceLineView(
    val id: Long,
    val orderItemId: Long,
    val feeItemId: Long,
    val coverageType: CoverageType,
    val unitPrice: Money,
    val quantity: Int,
    val lineTotal: Money,
)
