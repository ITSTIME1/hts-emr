package com.htsemr.payment.entity

import com.htsemr.billing.InvoiceView
import com.htsemr.common.Money
import com.htsemr.order.OrderItemStatus
import com.htsemr.payment.PaymentAmountExceededException
import com.htsemr.payment.PaymentLineStatus
import com.htsemr.payment.PaymentStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
internal class Payment private constructor(
    @Column(nullable = false) val invoiceId: Long,
    @Column(nullable = false) val visitId: Long,
    @Column(nullable = false) val counterId: Long,
    @Column(nullable = false) val cashierNo: String,
    @Column(nullable = false) val patientAmount: Money,
    @Column(nullable = false) val startedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.IN_PROGRESS
        protected set

    @Column
    var completedAt: LocalDateTime? = null
        protected set

    @OneToMany(cascade = [CascadeType.ALL])
    @JoinColumn(name = "payment_id", nullable = false)
    @OrderBy("id")
    private val itemEntities: MutableList<PaymentItem> = mutableListOf()

    @OneToMany(cascade = [CascadeType.ALL])
    @JoinColumn(name = "payment_id", nullable = false)
    @OrderBy("id")
    private val lineEntities: MutableList<PaymentLine> = mutableListOf()

    val orderItemIds: List<Long> get() = itemEntities.map { it.orderItemId }

    val lines: List<PaymentLine> get() = lineEntities.toList()

    val paidAmount: Money
        get() = lineEntities
            .filter { it.status == PaymentLineStatus.RECEIVED }
            .fold(Money.ZERO) { sum, line -> sum + line.amount }

    val remaining: Money get() = patientAmount - paidAmount

    val isFullyPaid: Boolean get() = paidAmount == patientAmount

    fun receiveCash(received: Money, amount: Money, receivedAt: LocalDateTime) {
        check(status == PaymentStatus.IN_PROGRESS) { "진행 중이 아닌 수납 건입니다: 수납 건 $id, $status" }
        val line = PaymentLine.cash(received, amount, receivedAt)
        val paidAfter = paidAmount + amount
        if (paidAfter > patientAmount) {
            throw PaymentAmountExceededException(invoiceId, patientAmount, paidAfter)
        }
        lineEntities.add(line)
    }

    fun complete(
        itemStatuses: Map<Long, OrderItemStatus>,
        alreadyPaidItemIds: Set<Long>,
        completedAt: LocalDateTime,
    ): CompletionBlockers {
        check(status == PaymentStatus.IN_PROGRESS) { "진행 중이 아닌 수납 건입니다: 수납 건 $id, $status" }
        check(isFullyPaid) { "결제 합계(${paidAmount.amount})가 본인부담 합계(${patientAmount.amount})에 이르지 않았습니다" }

        val blockers = CompletionBlockers(
            ineffectiveItemIds = orderItemIds.filterNot { itemStatuses.getValue(it).isEffective }.toSet(),
            alreadyPaidItemIds = orderItemIds.filter { it in alreadyPaidItemIds }.toSet(),
        )

        if (!blockers.isEmpty) return blockers

        status = PaymentStatus.COMPLETED
        this.completedAt = completedAt
        itemEntities.forEach { it.markCompleted() }
        return blockers
    }

    companion object {
        fun start(invoice: InvoiceView, counterId: Long, cashierNo: String, startedAt: LocalDateTime): Payment {
            require(cashierNo.isNotBlank()) { "수납자 번호가 없습니다" }
            require(invoice.lines.isNotEmpty()) { "산정 내역이 없습니다: 산정 ${invoice.id}" }
            return Payment(
                invoiceId = invoice.id,
                visitId = invoice.visitId,
                counterId = counterId,
                cashierNo = cashierNo,
                patientAmount = invoice.patientAmount,
                startedAt = startedAt,
            ).apply {
                itemEntities.addAll(invoice.lines.map { PaymentItem(it.orderItemId) })
            }
        }
    }
}
