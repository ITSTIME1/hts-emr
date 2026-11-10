package com.htsemr.order.mapping

import com.htsemr.order.OrderItemView
import com.htsemr.order.entity.OrderItem

/** 오더 항목에는 내원 건 id가 없으므로(오더에 있다) 부르는 쪽이 넘긴다. */
internal fun OrderItem.toOrderItemView(visitId: Long) = OrderItemView(
    id = requireNotNull(id) { "저장되지 않은 오더 항목" },
    orderId = orderId,
    visitId = visitId,
    feeItemId = feeItemId,
    quantity = quantity,
    status = status,
    executableBeforePayment = executableBeforePayment,
)
