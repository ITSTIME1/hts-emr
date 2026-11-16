package com.htsemr.payment

import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.common.Money
import com.htsemr.master.CounterNotFoundException
import com.htsemr.reception.VisitNotFoundException

interface PaymentService {

    fun requestInvoice(visitId: Long): Long

    fun receiveCash(invoiceId: Long, counterId: Long, cashierNo: String, received: Money, amount: Money): CashResult
}
