package com.htsemr.payment.repository

import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

internal interface PaymentRepository : JpaRepository<Payment, Long> {

    /**
     * 그 산정으로 만든, 주어진 상태의 수납 건. 없으면 null.
     *
     * 산정 하나에 수납 건은 하나라(INV-05, V9 유일 제약) 결과는 많아야 한 건이다.
     */
    fun findByInvoiceIdAndStatus(invoiceId: Long, status: PaymentStatus): Payment?

    /**
     * 그 산정으로 만든 수납 건이 상태와 상관없이 하나라도 있는지(A-22).
     *
     * 한 건만 돌려주는 findByInvoiceId가 아니라 exists인 이유: 필요한 것은 있는지뿐이다.
     * 유일 제약(V9)을 걸기 전에는, C-06에서 수납 건이 둘이 되면 한 건 조회가 결과 개수 예외로 재현을 가렸다.
     */
    fun existsByInvoiceId(invoiceId: Long): Boolean

    /** 주어진 오더 항목 가운데, 주어진 상태의 수납 건에 들어 있는 것. 수납하지 않은 항목을 고를 때 쓴다(A-02). */
    @Query(
        """
        select i.orderItemId from Payment p join p.itemEntities i
        where p.status = :status and i.orderItemId in :orderItemIds
        """
    )
    fun findOrderItemIdsIn(orderItemIds: Collection<Long>, status: PaymentStatus): List<Long>
}
