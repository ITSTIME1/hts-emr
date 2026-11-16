package com.htsemr.order.service

import com.htsemr.order.OrderItemNotFoundException
import com.htsemr.order.OrderItemQueryService
import com.htsemr.order.OrderItemStatus
import com.htsemr.order.OrderItemView
import com.htsemr.order.mapping.toOrderItemView
import com.htsemr.order.repository.OrderItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
internal class OrderItemQueryServiceImpl(
    private val orderItemRepository: OrderItemRepository,
) : OrderItemQueryService {

    override fun itemsOfVisit(visitId: Long): List<OrderItemView> {
        return orderItemRepository.findAllByVisitId(visitId)
            .map { it.toOrderItemView(visitId) }
    }

    override fun statusesOf(orderItemIds: Collection<Long>): Map<Long, OrderItemStatus> {
        val requestedIds = orderItemIds.toSet()
        val statuses = orderItemRepository.findAllById(requestedIds)
            .associate { requireNotNull(it.id) to it.status }

        val missingId = (requestedIds - statuses.keys).firstOrNull()
        if (missingId != null) {
            throw OrderItemNotFoundException(missingId)
        }
        return statuses
    }
}
