package com.htsemr.billing.service

import com.htsemr.billing.BillingService
import com.htsemr.billing.BillingTarget
import com.htsemr.billing.entity.Invoice
import com.htsemr.billing.entity.InvoiceLine
import com.htsemr.billing.entity.PricedItem
import com.htsemr.billing.repository.InvoiceLineRepository
import com.htsemr.billing.repository.InvoiceRepository
import com.htsemr.master.FeeQueryService
import com.htsemr.reception.EligibilityQueryService
import com.htsemr.reception.VisitQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
internal class BillingServiceImpl(

    private val visitQueryService : VisitQueryService,
    private val eligibilityQueryService : EligibilityQueryService,
    private val feeQueryService : FeeQueryService,
    private val invoiceRepository: InvoiceRepository,
    private val invoiceLineRepository: InvoiceLineRepository,
)  : BillingService {

    override fun calculate(
        visitId: Long,
        targets: List<BillingTarget>
    ): Long {

        require(targets.isNotEmpty()) { "대상 항목이 없습니다" }

        val visit = visitQueryService.visit(visitId = visitId)

        val visitDate = visit.visitDate

        val eligibility = eligibilityQueryService.currentEligibility(visitId = visit.id)

        val items : List<PricedItem> = targets.map { target ->
            val feeItem = feeQueryService.feeItem(feeItemId = target.feeItemId)
            val price = feeQueryService.priceOn(feeItemId = target.feeItemId, visitDate = visitDate)

            PricedItem(
                orderItemId = target.orderItemId,
                feeItemId = feeItem.id,
                coverageType = feeItem.coverageType,
                unitPrice = price,
                quantity = target.quantity
            )
        }

        val invoice = invoiceRepository.save(Invoice.calculate(eligibility, items, LocalDateTime.now()))
        val invoiceId = requireNotNull(invoice.id) { "저장된 산정에 id가 없습니다" }

        invoiceLineRepository.saveAll(items.map { InvoiceLine.of(invoiceId, it) })
        return invoiceId
    }
}
