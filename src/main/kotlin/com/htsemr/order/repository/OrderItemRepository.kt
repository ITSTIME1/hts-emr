package com.htsemr.order.repository

import com.htsemr.order.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

internal interface OrderItemRepository : JpaRepository<OrderItem, Long> {

    @Query("select i from OrderItem i join MedicalOrder o on o.id = i.orderId where o.visitId = :visitId order by i.id")
    fun findAllByVisitId(visitId: Long): List<OrderItem>
}
