package com.htsemr.order

interface OrderItemQueryService {

    fun itemsOfVisit(visitId: Long): List<OrderItemView>

    fun statusesOf(orderItemIds: Collection<Long>): Map<Long, OrderItemStatus>
}
