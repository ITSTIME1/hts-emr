package com.htsemr.billing.service

import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.billing.InvoiceView
import com.htsemr.billing.mapping.toInvoiceView
import com.htsemr.billing.repository.InvoiceLineRepository
import com.htsemr.billing.repository.InvoiceRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
internal class InvoiceQueryServiceImpl(
    private val invoiceRepository: InvoiceRepository,
    private val invoiceLineRepository: InvoiceLineRepository,
) : InvoiceQueryService {

    override fun invoice(invoiceId: Long): InvoiceView {
        val invoice = invoiceRepository.findByIdOrNull(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)

        val lines = invoiceLineRepository.findAllByInvoiceIdOrderById(invoiceId)

        return invoice.toInvoiceView(lines)
    }
}
