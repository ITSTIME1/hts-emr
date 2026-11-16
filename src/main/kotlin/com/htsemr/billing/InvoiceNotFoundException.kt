package com.htsemr.billing

class InvoiceNotFoundException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 이 없습니다")
