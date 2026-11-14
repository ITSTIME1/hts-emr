package com.htsemr.payment.mapping

import com.htsemr.payment.PaymentLineView
import com.htsemr.payment.PaymentView
import com.htsemr.payment.entity.Payment
import com.htsemr.payment.entity.PaymentLine

internal fun Payment.toPaymentView() = PaymentView(
    id = requireNotNull(id) { "저장되지 않은 수납 건" },
    invoiceId = invoiceId,
    visitId = visitId,
    counterId = counterId,
    cashierNo = cashierNo,
    patientAmount = patientAmount,
    paidAmount = paidAmount,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    orderItemIds = orderItemIds,
    lines = lines.map { it.toPaymentLineView() },
)

internal fun PaymentLine.toPaymentLineView() = PaymentLineView(
    id = requireNotNull(id) { "저장되지 않은 결제" },
    method = method,
    status = status,
    receivedAmount = receivedAmount,
    appliedAmount = appliedAmount,
    change = change,
    receivedAt = receivedAt,
)
