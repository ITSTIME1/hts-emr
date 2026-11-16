package com.htsemr.payment.service

import com.htsemr.billing.BillingService
import com.htsemr.billing.BillingTarget
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.common.Money
import com.htsemr.master.CounterQueryService
import com.htsemr.order.OrderItemQueryService
import com.htsemr.payment.CashResult
import com.htsemr.payment.InvoiceAlreadyUsedException
import com.htsemr.payment.NothingToPayException
import com.htsemr.payment.PaymentService
import com.htsemr.payment.PaymentStartConflictException
import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.entity.Payment
import com.htsemr.payment.repository.PaymentRepository
import com.htsemr.reception.VisitQueryService
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
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
 * - 산정 하나에 수납 건 하나(INV-05): 수납 건을 새로 만들기 전에 서비스가 확인하고, 동시에 부를 때는 DB 유일 제약(V9)이 막는다
 *
 * 그 밖의 동시 호출 방어(C-03)는 아직 없다(payment.md 7절, ADR-0003). 틈이 있는 곳에는 주석으로 표시했다.
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
        amount: Money,
    ): CashResult {
        val now = LocalDateTime.now()

        // 1. 수납 건을 정한다.
        //    이 산정의 진행 중인 수납 건이 있으면 이어서 쓴다(T-PAY-02).
        //    없으면(null) 두 경우가 섞여 있어서 existsByInvoiceId로 가른다.
        //    (가) 이 산정으로 만든 수납 건이 아직 없다 → 새로 만든다(T-PAY-01).
        //    (나) 있지만 진행 중이 아니다(COMPLETED, REJECTED, HELD) → 새로 만들면 안 된다.
        //        한 산정으로는 수납 건 하나다(A-22, INV-05). 다시 받으려면 새로 산정해야 한다.
        //        슬라이스 1에서 (나)는 COMPLETED뿐이다. REJECTED, HELD는 거부·보류를 만들 때 생긴다.
        //    산정 자체가 없으면(잘못된 번호) 그다음 invoice 조회에서 InvoiceNotFoundException이 난다.
        //    두 창구가 같은 순간에 첫 결제를 받으면 둘 다 (가)를 보고 둘 다 만들려 한다(C-06).
        //    이 확인으로는 못 막고, payment.invoice_id 유일 제약(V9)이 뒤쪽 insert를 실패시킨다.
        //    뒤쪽 창구는 save에서 PaymentStartConflictException을 받는다(아래 save 참고).
        val payment = paymentRepository.findByInvoiceIdAndStatus(invoiceId, PaymentStatus.IN_PROGRESS)
            ?: run {
                if (paymentRepository.existsByInvoiceId(invoiceId)) throw InvoiceAlreadyUsedException(invoiceId)
                val invoice = invoiceQueryService.invoice(invoiceId)
                val counter = counterQueryService.counter(counterId)
                Payment.start(invoice, counter.id, cashierNo, now)
            }

        // 2. 결제를 붙인다. 메모리 속 수납 건에 붙이기만 하고, 저장은 3이나 5에서 한다.
        //    결제 합계가 본인부담 합계를 넘으면 PaymentAmountExceededException이 난다(A-20).
        //    그러면 트랜잭션이 롤백되어, 이 호출에서 새로 만든 수납 건도 남지 않는다.
        payment.receiveCash(received, amount, now)

        // 3. 아직 덜 냈으면 진행 중으로 저장하고 남은 금액을 알린다. 여기서 끝난다.
        //    isFullyPaid는 저장된 값이 아니라 부를 때마다 계산하므로, 2에서 붙인 결제까지 반영돼 있다.
        //    새 수납 건이면 save에서 id가 생긴다(IDENTITY).
        if (!payment.isFullyPaid) {
            val saved = save(payment)
            return CashResult.InProgress(requireNotNull(saved.id), saved.remaining)
        }

        // 4. 여기까지 왔으면 다 냈다. 완료 조건(T-PAY-03)의 재료를 모아 완료를 시도한다. 판단은 Payment.complete가 한다.
        //    - 항목의 지금 상태: order에 묻는다(INV-04).
        //    - 다른 완료된 수납 건에 이미 들어 있는 항목: 이 수납 건은 다른 수납 건을 볼 수 없으므로 여기서 찾는다(INV-03).
        //    틈: 두 수납 건이 같은 순간에 완료하면 둘 다 "없다"를 보고 둘 다 완료한다(C-03, ADR-0003).
        val statuses = orderItemQueryService.statusesOf(payment.orderItemIds)
        val alreadyPaidItemIds =
            paymentRepository.findOrderItemIdsIn(payment.orderItemIds, PaymentStatus.COMPLETED).toSet()
        val blockers = payment.complete(statuses, alreadyPaidItemIds, now)

        val saved = save(payment)
        val paymentId = requireNotNull(saved.id)
        return if (blockers.isEmpty) {
            CashResult.Completed(paymentId)
        } else {
            CashResult.CompletionBlocked(paymentId, blockers.ineffectiveItemIds, blockers.alreadyPaidItemIds)
        }
    }

    /**
     * 수납 건을 저장한다. 새 수납 건이 같은 산정의 유일 제약(V9)에 걸리면 [PaymentStartConflictException]으로 바꾼다.
     *
     * 새 수납 건은 save에서 바로 insert된다(IDENTITY). 다른 창구가 같은 산정으로 먼저 insert했다면,
     * 그 트랜잭션이 끝날 때까지 기다렸다가 커밋되면 여기서 제약 위반이 난다(C-06).
     * 다른 제약 위반은 이 경우가 아니므로 그대로 던진다.
     */
    private fun save(payment: Payment): Payment {
        val isNew = payment.id == null
        try {
            return paymentRepository.save(payment)
        } catch (e: DataIntegrityViolationException) {
            if (isNew && e.violates(INVOICE_UNIQUE_CONSTRAINT)) throw PaymentStartConflictException(payment.invoiceId)
            throw e
        }
    }

    /** 원인을 따라가며 Hibernate가 알아낸 제약 이름을 찾는다. Oracle은 "스키마.제약" 꼴로 알려 준다. */
    private fun DataIntegrityViolationException.violates(constraint: String): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .any { it.constraintName?.substringAfterLast('.').equals(constraint, ignoreCase = true) }

    private companion object {
        /** payment.invoice_id 유일 제약(V9). 한 산정에 수납 건 하나(INV-05). */
        const val INVOICE_UNIQUE_CONSTRAINT = "UK_PAYMENT_INVOICE"
    }
}
