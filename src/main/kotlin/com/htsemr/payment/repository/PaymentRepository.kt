package com.htsemr.payment.repository

import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

internal interface PaymentRepository : JpaRepository<Payment, Long> {

    /**
     * 그 산정으로 만든, 주어진 상태의 수납 건. 없으면 null.
     *
     * 슬라이스 1은 산정 하나에 수납 건이 하나라고 가정한다(INV-05). 이를 막는 제약은 일부러 없으므로,
     * 둘 이상이면 Spring Data가 예외를 던진다. 그 상황이 C-06의 재현 대상이다.
     */
    fun findByInvoiceIdAndStatus(invoiceId: Long, status: PaymentStatus): Payment?

    /** 주어진 오더 항목 가운데, 주어진 상태의 수납 건에 들어 있는 것. 수납하지 않은 항목을 고를 때 쓴다(A-02). */
    @Query(
        """
        select i.orderItemId from Payment p join p.itemEntities i
        where p.status = :status and i.orderItemId in :orderItemIds
        """
    )
    fun findOrderItemIdsIn(orderItemIds: Collection<Long>, status: PaymentStatus): List<Long>
}
