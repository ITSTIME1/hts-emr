package com.htsemr.order.api.dto

import com.htsemr.order.OrderItemStatus
import com.htsemr.order.OrderItemView

internal data class OrderItemResponse(
    val id: Long,
    val orderId: Long,
    val feeItemId: Long,
    val quantity: Int,
    val status: OrderItemStatus,
    val executableBeforePayment: Boolean,
) {
    companion object {
        fun from(view: OrderItemView) = OrderItemResponse(
            id = view.id,
            orderId = view.orderId,
            feeItemId = view.feeItemId,
            quantity = view.quantity,
            status = view.status,
            executableBeforePayment = view.executableBeforePayment
        )
    }
}