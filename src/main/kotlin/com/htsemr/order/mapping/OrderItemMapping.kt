package com.htsemr.order.mapping

import com.htsemr.order.OrderItemView
import com.htsemr.order.entity.OrderItem

internal fun OrderItem.toOrderItemView(visitId: Long) = OrderItemView(
    id = requireNotNull(id) { "저장되지 않은 오더 항목" },
    orderId = orderId,
    visitId = visitId,
    feeItemId = feeItemId,
    quantity = quantity,
    status = status,
    executableBeforePayment = executableBeforePayment,
)
