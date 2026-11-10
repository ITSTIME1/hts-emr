package com.htsemr.order.repository

import com.htsemr.order.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

internal interface OrderItemRepository : JpaRepository<OrderItem, Long> {

    /** 내원 건의 오더 항목. 오더 항목은 내원 건 id를 갖지 않으므로 오더를 거쳐 찾는다. */
    @Query("select i from OrderItem i join MedicalOrder o on o.id = i.orderId where o.visitId = :visitId order by i.id")
    fun findAllByVisitId(visitId: Long): List<OrderItem>
}
