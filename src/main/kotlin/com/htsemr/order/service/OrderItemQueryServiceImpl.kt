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
        // 쿼리가 이미 visitId로 걸렀으므로, 모든 항목의 내원 건 id는 인자로 받은 값이다.
        return orderItemRepository.findAllByVisitId(visitId)
            .map { it.toOrderItemView(visitId) }
    }

    override fun statusesOf(orderItemIds: Collection<Long>): Map<Long, OrderItemStatus> {
        // 1. 중복 id를 걷어 내고 한 번에 읽는다.
        val requestedIds = orderItemIds.toSet()
        val statuses = orderItemRepository.findAllById(requestedIds)
            .associate { requireNotNull(it.id) to it.status }

        // 2. 요청했는데 읽히지 않은 id가 있으면 거부한다(차집합).
        val missingId = (requestedIds - statuses.keys).firstOrNull()
        if (missingId != null) {
            throw OrderItemNotFoundException(missingId)
        }
        return statuses
    }
}
