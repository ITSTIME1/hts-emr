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

/**
 * 수납 건(Payment). 포함한 오더 항목과 결제를 함께 다룬다.
 *
 * [결제와 항목을 수납 건이 들고 있는 이유]
 * "결제 합계가 본인부담 합계를 넘지 않는다"(A-20, INV-06)는 수납 건과 결제를 함께 봐야 지킬 수 있다.
 * 그래서 결제 추가와 합계 확인을 이 클래스 한 곳에서 한다. order의 오더와 오더 항목에는 이런 규칙이 없어 따로 두었다.
 *
 * 결제와 항목은 이 클래스의 메서드로만 늘어나고, 밖에는 읽기 전용 목록으로만 보인다.
 * 저장은 수납 건을 저장할 때 함께 된다(cascade).
 */
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

    /**
     * 지금 상태. 전이 메서드로만 바꾼다.
     * private set이 아니라 protected set인 이유는 OrderItem.status와 같다(allOpen과 open 프로퍼티).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.IN_PROGRESS
        protected set

    @Column
    var completedAt: LocalDateTime? = null
        protected set

    // payment_item, payment_line의 payment_id는 이 연관이 채운다(단방향 @OneToMany + @JoinColumn).
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

    /** 결제 합계. 받은(RECEIVED) 결제의 수납 반영 금액만 더한다(T-PAY-03). */
    val paidAmount: Money
        get() = lineEntities
            .filter { it.status == PaymentLineStatus.RECEIVED }
            .fold(Money.ZERO) { sum, line -> sum + line.appliedAmount }

    /** 남은 금액. */
    val remaining: Money get() = patientAmount - paidAmount

    val isFullyPaid: Boolean get() = paidAmount == patientAmount

    /**
     * 현금 수령(T-PAY-02, T-PLN-12). 진행 중인 수납 건에만 받는다.
     *
     * @throws PaymentAmountExceededException 이 결제를 더하면 결제 합계가 본인부담 합계를 넘을 때(A-20)
     */
    fun receiveCash(received: Money, applied: Money, receivedAt: LocalDateTime) {
        check(status == PaymentStatus.IN_PROGRESS) { "진행 중이 아닌 수납 건입니다: 수납 건 $id, $status" }
        val line = PaymentLine.cash(received, applied, receivedAt)
        val paidAfter = paidAmount + applied
        if (paidAfter > patientAmount) {
            throw PaymentAmountExceededException(invoiceId, patientAmount, paidAfter)
        }
        lineEntities.add(line)
    }

    /**
     * 수납 완료(T-PAY-03). 결제 합계가 본인부담 합계와 같을 때만 부른다.
     *
     * 포함한 항목이 모두 효력이 있고(INV-04) 다른 완료된 수납 건에 없으면(INV-03) 완료하고, 빈 [CompletionBlockers]를 돌려준다.
     * 하나라도 걸리면 완료하지 않고 걸린 항목들을 이유별로 돌려준다. 상태는 진행 중 그대로다.
     *
     * 다른 수납 건은 이 수납 건이 볼 수 없으므로, 서비스가 찾아서 넘기고 판단은 여기서 한다.
     *
     * @param itemStatuses 포함한 항목의 지금 상태. 서비스가 order의 공개 조회로 가져온다. 포함한 항목이 모두 들어 있어야 한다.
     * @param alreadyPaidItemIds 다른 완료된 수납 건에 이미 들어 있는 항목. 서비스가 리포지토리로 찾는다.
     */
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
        return blockers
    }

    companion object {
        /**
         * 수납 건 생성(T-PAY-01). 산정에서 내원 건, 본인부담 합계, 포함할 오더 항목을 복사한다.
         * 산정은 만든 뒤 바뀌지 않으므로 복사본이 어긋나지 않는다.
         *
         * 생성자를 막고 이 메서드로만 만드는 이유: 산정과 어긋난 수납 건(다른 내원 건, 다른 금액)이 만들어질 길을 없앤다.
         */
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
