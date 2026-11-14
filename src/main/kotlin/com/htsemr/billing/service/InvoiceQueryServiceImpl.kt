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
        // 1. 산정 찾기
        val invoice = invoiceRepository.findByIdOrNull(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)

        // 2. 내역을 id 순서로 가져오기.
        //    파라미터를 그대로 쓴다. 바로 위에서 같은 값으로 산정을 찾았으므로 이미 확인된 값이고,
        //    invoice.id는 Long?이라 다시 requireNotNull을 거쳐야 한다.
        val lines = invoiceLineRepository.findAllByInvoiceIdOrderById(invoiceId)

        // 3. 공개 타입으로 바꿔 돌려주기
        return invoice.toInvoiceView(lines)
    }
}
