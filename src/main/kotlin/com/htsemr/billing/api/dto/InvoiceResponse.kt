package com.htsemr.billing.api.dto

import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceView
import com.htsemr.master.CoverageType
import com.htsemr.reception.InsuranceType
import java.time.LocalDateTime

internal data class InvoiceResponse(
    val id: Long,
    val visitId: Long,
    val eligibilitySeq: Int,
    val insuranceType: InsuranceType,
    val copayRate: Int,
    val coveredLineSum: Long,
    val coveredTotal: Long,
    val coveredCopay: Long,
    val insurerAmount: Long,
    val nonCoveredTotal: Long,
    val patientAmount: Long,
    val calculatedAt: LocalDateTime,
    val lines: List<InvoiceLineResponse>,
) {
    companion object {
        fun from(view: InvoiceView) = InvoiceResponse(
            id = view.id,
            visitId = view.visitId,
            eligibilitySeq = view.eligibilitySeq,
            insuranceType = view.insuranceType,
            copayRate = view.copayRate,
            coveredLineSum = view.coveredLineSum.amount,
            coveredTotal = view.coveredTotal.amount,
            coveredCopay = view.coveredCopay.amount,
            insurerAmount = view.insurerAmount.amount,
            nonCoveredTotal = view.nonCoveredTotal.amount,
            patientAmount = view.patientAmount.amount,
            calculatedAt = view.calculatedAt,
            lines = view.lines.map { InvoiceLineResponse.from(it) },
        )
    }
}

internal data class InvoiceLineResponse(
    val id: Long,
    val orderItemId: Long,
    val feeItemId: Long,
    val coverageType: CoverageType,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
) {
    companion object {
        fun from(view: InvoiceLineView) = InvoiceLineResponse(
            id = view.id,
            orderItemId = view.orderItemId,
            feeItemId = view.feeItemId,
            coverageType = view.coverageType,
            unitPrice = view.unitPrice.amount,
            quantity = view.quantity,
            lineTotal = view.lineTotal.amount,
        )
    }
}
