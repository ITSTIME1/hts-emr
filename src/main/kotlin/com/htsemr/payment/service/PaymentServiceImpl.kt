package com.htsemr.payment.service

import com.htsemr.billing.BillingService
import com.htsemr.billing.BillingTarget
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.common.Money
import com.htsemr.master.CounterQueryService
import com.htsemr.observability.DomainEventLog
import com.htsemr.order.OrderItemQueryService
import com.htsemr.payment.CashResult
import com.htsemr.payment.InvoiceAlreadyUsedException
import com.htsemr.payment.NothingToPayException
import com.htsemr.payment.PaymentCompletionConflictException
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

        val payment = paymentRepository.findByInvoiceIdAndStatus(invoiceId, PaymentStatus.IN_PROGRESS)
            ?: run {
                if (paymentRepository.existsByInvoiceId(invoiceId)) throw InvoiceAlreadyUsedException(invoiceId)
                val invoice = invoiceQueryService.invoice(invoiceId)
                val counter = counterQueryService.counter(counterId)
                Payment.start(invoice, counter.id, cashierNo, now)
            }

        val isNew = payment.id == null

        payment.receiveCash(received, amount, now)

        if (!payment.isFullyPaid) {
            val saved = save(payment)
            val paymentId = requireNotNull(saved.id)
            emitCashEvents(saved, paymentId, isNew, counterId, received, amount)
            return CashResult.InProgress(paymentId, saved.remaining)
        }

        val statuses = orderItemQueryService.statusesOf(payment.orderItemIds)
        val alreadyPaidItemIds =
            paymentRepository.findOrderItemIdsIn(payment.orderItemIds, PaymentStatus.COMPLETED).toSet()
        val blockers = payment.complete(statuses, alreadyPaidItemIds, now)

        val saved = save(payment)
        val paymentId = requireNotNull(saved.id)
        emitCashEvents(saved, paymentId, isNew, counterId, received, amount)
        return if (blockers.isEmpty) {
            DomainEventLog.emit(
                "payment.completed",
                "paymentId" to paymentId,
                "invoiceId" to saved.invoiceId,
                "itemCount" to saved.orderItemIds.size,
            )
            CashResult.Completed(paymentId)
        } else {
            DomainEventLog.emit(
                "payment.completionBlocked",
                "paymentId" to paymentId,
                "invoiceId" to saved.invoiceId,
                "ineffectiveItemIds" to blockers.ineffectiveItemIds.sorted(),
                "alreadyPaidItemIds" to blockers.alreadyPaidItemIds.sorted(),
            )
            CashResult.CompletionBlocked(paymentId, blockers.ineffectiveItemIds, blockers.alreadyPaidItemIds)
        }
    }

    private fun emitCashEvents(
        payment: Payment,
        paymentId: Long,
        isNew: Boolean,
        counterId: Long,
        received: Money,
        applied: Money,
    ) {
        if (isNew) {
            DomainEventLog.emit(
                "payment.started",
                "paymentId" to paymentId,
                "invoiceId" to payment.invoiceId,
                "visitId" to payment.visitId,
                "counterId" to counterId,
                "cashierNo" to payment.cashierNo,
            )
        }
        DomainEventLog.emit(
            "payment.cashReceived",
            "paymentId" to paymentId,
            "invoiceId" to payment.invoiceId,
            "applied" to applied.amount,
            "received" to received.amount,
            "remaining" to payment.remaining.amount,
        )
    }

    private fun save(payment: Payment): Payment {
        val isNew = payment.id == null
        try {
            return paymentRepository.saveAndFlush(payment)
        } catch (e: DataIntegrityViolationException) {
            if (isNew && e.violates(INVOICE_UNIQUE_CONSTRAINT)) throw PaymentStartConflictException(payment.invoiceId)
            if (e.violates(COMPLETED_ITEM_UNIQUE_INDEX)) throw PaymentCompletionConflictException(payment.invoiceId)
            throw e
        }
    }

    private fun DataIntegrityViolationException.violates(constraint: String): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .any { it.constraintName?.substringAfterLast('.').equals(constraint, ignoreCase = true) }

    private companion object {
        const val INVOICE_UNIQUE_CONSTRAINT = "UK_PAYMENT_INVOICE"

        const val COMPLETED_ITEM_UNIQUE_INDEX = "UK_PAYMENT_ITEM_COMPLETED"
    }
}
