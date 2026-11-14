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

        // 1. 대상 확인, 내원 건 확인 → 진료일
        require(targets.isNotEmpty()) { "대상 항목이 없습니다" }

        // 내원건이 존재하지 않으면 VisitNotFoundException 발생
        val visit = visitQueryService.visit(visitId = visitId)

        // 내원 일자 추출
        val visitDate = visit.visitDate

        // 2. 내원ID를 기준으로 현재 자격을 조회
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

        // 4. 산정 계산·저장. 계산은 Invoice.calculate가 하고, 서비스는 재료만 넘긴다.
        //    내원 건 id는 자격에서 가져가므로, 2단계에서 확인한 내원 건(visit.id)으로 저장된다.
        val invoice = invoiceRepository.save(Invoice.calculate(eligibility, items, LocalDateTime.now()))
        val invoiceId = requireNotNull(invoice.id) { "저장된 산정에 id가 없습니다" }

        // 5. 산정 내역 저장. 합계와 같은 PricedItem을 쓰므로 합계와 내역의 금액이 어긋나지 않는다.
        invoiceLineRepository.saveAll(items.map { InvoiceLine.of(invoiceId, it) })
        return invoiceId
    }
}