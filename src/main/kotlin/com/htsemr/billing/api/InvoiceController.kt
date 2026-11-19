package com.htsemr.billing.api

import com.htsemr.billing.InvoiceQueryService
import com.htsemr.billing.api.dto.InvoiceResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/invoices")
internal class InvoiceController(
    private val invoiceQueryService: InvoiceQueryService,
) {

    @GetMapping("/{invoiceId}")
    fun getInvoice(@PathVariable invoiceId: Long): InvoiceResponse =
        InvoiceResponse.from(invoiceQueryService.invoice(invoiceId))
}
