package com.htsemr.billing.repository

import com.htsemr.billing.entity.InvoiceLine
import org.springframework.data.jpa.repository.JpaRepository

internal interface InvoiceLineRepository : JpaRepository<InvoiceLine, Long> {

    fun findAllByInvoiceIdOrderById(invoiceId: Long): List<InvoiceLine>
}
