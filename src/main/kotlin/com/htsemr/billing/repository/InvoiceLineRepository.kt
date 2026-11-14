package com.htsemr.billing.repository

import com.htsemr.billing.entity.InvoiceLine
import org.springframework.data.jpa.repository.JpaRepository

internal interface InvoiceLineRepository : JpaRepository<InvoiceLine, Long> {

    /** 산정의 내역. id 순서다. */
    fun findAllByInvoiceIdOrderById(invoiceId: Long): List<InvoiceLine>
}
