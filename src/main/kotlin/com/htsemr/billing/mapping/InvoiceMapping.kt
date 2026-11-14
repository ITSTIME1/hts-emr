package com.htsemr.billing.mapping

import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceView
import com.htsemr.billing.entity.Invoice
import com.htsemr.billing.entity.InvoiceLine

/** 산정에는 내역이 들어 있지 않으므로(내역이 산정 id를 가리킨다) 부르는 쪽이 넘긴다. */
internal fun Invoice.toInvoiceView(lines: List<InvoiceLine>) = InvoiceView(
    id = requireNotNull(id) { "저장되지 않은 산정" },
    visitId = visitId,
    eligibilitySeq = eligibilitySeq,
    insuranceType = insuranceType,
    copayRate = copayRate,
    coveredLineSum = coveredLineSum,
    coveredTotal = coveredTotal,
    coveredCopay = coveredCopay,
    insurerAmount = insurerAmount,
    nonCoveredTotal = nonCoveredTotal,
    patientAmount = patientAmount,
    calculatedAt = calculatedAt,
    lines = lines.map { it.toInvoiceLineView() },
)

internal fun InvoiceLine.toInvoiceLineView() = InvoiceLineView(
    id = requireNotNull(id) { "저장되지 않은 산정 내역" },
    orderItemId = orderItemId,
    feeItemId = feeItemId,
    coverageType = coverageType,
    unitPrice = unitPrice,
    quantity = quantity,
    lineTotal = lineTotal,
)
