package com.htsemr.order.repository

import com.htsemr.order.entity.OrderItemCancellation
import org.springframework.data.jpa.repository.JpaRepository

internal interface OrderItemCancellationRepository : JpaRepository<OrderItemCancellation, Long>
