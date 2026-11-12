package com.htsemr.billing.repository

import com.htsemr.billing.entity.Invoice
import org.springframework.data.jpa.repository.JpaRepository

internal interface InvoiceRepository : JpaRepository<Invoice, Long>
