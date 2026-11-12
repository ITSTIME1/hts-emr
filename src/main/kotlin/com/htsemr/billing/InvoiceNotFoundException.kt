package com.htsemr.billing

/** 산정이 없다. */
class InvoiceNotFoundException(val invoiceId: Long)
    : RuntimeException("산정 $invoiceId 이 없습니다")
