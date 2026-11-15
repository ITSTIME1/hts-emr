package com.htsemr.payment.service

import com.htsemr.billing.BillingService
import com.htsemr.billing.BillingTarget
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.common.Money
import com.htsemr.master.CounterQueryService
import com.htsemr.order.OrderItemQueryService
import com.htsemr.payment.CashResult
import com.htsemr.payment.InvoiceAlreadyPaidException
import com.htsemr.payment.NothingToPayException
import com.htsemr.payment.PaymentService
import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.entity.Payment
import com.htsemr.payment.repository.PaymentRepository
import com.htsemr.reception.VisitQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 수납(슬라이스 1: 현금 수납의 정상 흐름).
 *
 * 서비스는 다른 모듈에서 재료를 모아 오고, 규칙은 Payment가 지킨다.
 * - 결제 합계 ≤ 본인부담 합계(A-20): Payment.receiveCash
 * - 완료 직전 항목 효력(INV-04): 서비스가 order에서 상태를 가져오고, Payment.complete가 판단
 * - 완료 직전 다른 수납 건과의 중복(INV-03): 서비스가 리포지토리로 찾고, Payment.complete가 판단
 * - 산정 하나에 수납 건 하나(INV-05): 수납 건을 새로 만들기 전에 서비스가 확인
 *
 * 동시에 부를 때의 이중 수납 방어는 일부러 없다(payment.md 7절, ADR-0003). 틈이 있는 곳에는 주석으로 표시했다.
 */
@Service
@Transactional
internal class PaymentServiceImpl(
    private val visitQueryService: VisitQueryService,
    private val orderItemQueryService: OrderItemQueryService,
    private val billingService: BillingService,
    private val invoiceQueryService: InvoiceQueryService,
    private val counterQueryService: CounterQueryService,
    private val paymentRepository: PaymentRepository,
) : PaymentService {

    override fun requestInvoice(visitId: Long): Long {
        val visit = visitQueryService.visit(visitId)

        val effectiveItems = orderItemQueryService.itemsOfVisit(visit.id).filter { it.status.isEffective }

        val paidItemIds: Set<Long> = if (effectiveItems.isEmpty()) emptySet() else
            paymentRepository.findOrderItemIdsIn(effectiveItems.map { it.id }, PaymentStatus.COMPLETED).toSet()

        val targets = effectiveItems
            .filter { it.id !in paidItemIds }
            .map { BillingTarget(orderItemId = it.id, feeItemId = it.feeItemId, quantity = it.quantity) }
        if (targets.isEmpty()) throw NothingToPayException(visit.id)

        return billingService.calculate(visit.id, targets)
    }

    override fun receiveCash(
        invoiceId: Long,
        counterId: Long,
        cashierNo: String,
        received: Money,
        applied: Money,
    ): CashResult {
        val now = LocalDateTime.now()

        // 1. 그 산정으로 진행 중인 수납 건이 있으면 이어서 쓴다(T-PAY-02).
        // 2. 없으면 수납 건을 새로 만든다(T-PAY-01). 단, 그 산정의 수납 건이 이미 있으면(완료 등) 거부한다(A-22, INV-05).
        //    "진행 중이 없다"에는 "아직 시작 안 함"과 "이미 끝남"이 섞여 있어서, 둘을 여기서 가른다.
        //    이 결제는 받으면 안 되는 돈이므로 예외(롤백)가 맞다. 아직 아무것도 기록하지 않았다.
        //    순서대로 부른 경우는 이 확인으로 막힌다. 두 창구가 같은 순간에 첫 결제를 받으면 둘 다 "없다"를 보고
        //    둘 다 만든다. 확인과 생성 사이의 이 틈은 C-06의 재현 대상이다(ADR-0003).
        val payment = paymentRepository.findByInvoiceIdAndStatus(invoiceId, PaymentStatus.IN_PROGRESS)
            ?: run {
                if (paymentRepository.existsByInvoiceId(invoiceId)) throw InvoiceAlreadyPaidException(invoiceId)
                val invoice = invoiceQueryService.invoice(invoiceId)
                val counter = counterQueryService.counter(counterId)
                Payment.start(invoice, counter.id, cashierNo, now)
            }

        payment.receiveCash(received, applied, now)

        // 3. 아직 덜 냈으면 진행 중으로 저장한다. 새 수납 건이면 save에서 id가 생긴다(IDENTITY).
        if (!payment.isFullyPaid) {
            val saved = paymentRepository.save(payment)
            return CashResult.InProgress(requireNotNull(saved.id), saved.remaining)
        }

        // 4. 다 냈으면 완료 조건(T-PAY-03)의 재료를 모아 완료를 시도한다. 판단은 Payment.complete가 한다.
        //    - 항목의 지금 상태: order에 묻는다(INV-04).
        //    - 다른 완료된 수납 건에 이미 들어 있는 항목: 이 수납 건은 다른 수납 건을 볼 수 없으므로 여기서 찾는다(INV-03).
        //      지금 수납 건은 아직 진행 중이라 결과에 섞이지 않는다.
        //    순서대로 부른 경우는 이 확인으로 막힌다. 확인과 완료 사이의 틈(동시에 완료하는 C-03, 동시에 취소되는 C-04)은
        //    ADR-0003의 재현 대상이다.
        val statuses = orderItemQueryService.statusesOf(payment.orderItemIds)
        val alreadyPaidItemIds =
            paymentRepository.findOrderItemIdsIn(payment.orderItemIds, PaymentStatus.COMPLETED).toSet()
        val blockers = payment.complete(statuses, alreadyPaidItemIds, now)

        // 5. 완료가 막혀도 예외가 아니라 결과로 알린다. 예외를 던지면 위에서 받은 현금 기록까지 롤백된다(payment.md 7절).
        val saved = paymentRepository.save(payment)
        val paymentId = requireNotNull(saved.id)
        return if (blockers.isEmpty) {
            CashResult.Completed(paymentId)
        } else {
            CashResult.CompletionBlocked(paymentId, blockers.ineffectiveItemIds, blockers.alreadyPaidItemIds)
        }
    }
}
