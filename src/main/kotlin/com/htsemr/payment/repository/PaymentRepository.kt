package com.htsemr.payment.repository

import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

internal interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByInvoiceIdAndStatus(invoiceId: Long, status: PaymentStatus): Payment?

    fun existsByInvoiceId(invoiceId: Long): Boolean

    @Query(
        """
        select i.orderItemId from Payment p join p.itemEntities i
        where p.status = :status and i.orderItemId in :orderItemIds
        """
    )
    fun findOrderItemIdsIn(orderItemIds: Collection<Long>, status: PaymentStatus): List<Long>
}
