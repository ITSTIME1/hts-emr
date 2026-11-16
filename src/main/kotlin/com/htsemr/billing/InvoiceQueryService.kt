package com.htsemr.billing

interface InvoiceQueryService {

    fun invoice(invoiceId: Long): InvoiceView
}
