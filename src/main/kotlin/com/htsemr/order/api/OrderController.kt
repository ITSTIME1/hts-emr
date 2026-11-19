package com.htsemr.order.api

import com.htsemr.order.NewOrderItem
import com.htsemr.order.OrderItemQueryService
import com.htsemr.order.OrderService
import com.htsemr.order.api.dto.CancelOrderItemRequest
import com.htsemr.order.api.dto.OrderItemListResponse
import com.htsemr.order.api.dto.OrderItemResponse
import com.htsemr.order.api.dto.PlaceOrderRequest
import com.htsemr.order.api.dto.PlacedOrder
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1")
internal class OrderController(
    private val orderService: OrderService,
    private val orderItemQueryService: OrderItemQueryService,
) {

    @PostMapping("/visits/{visitId}/orders")
    fun placeOrder(
        @PathVariable visitId: Long,
        @Valid @RequestBody request: PlaceOrderRequest,
    ): ResponseEntity<PlacedOrder> {
        val orderId = orderService.placeOrder(
            visitId = visitId,
            doctorNo = request.doctorNo,
            items = request.orderItems.map { item ->
                NewOrderItem(
                    feeItemId = item.feeItemId,
                    quantity = item.quantity,
                    approvedByDoctor = item.approvedByDoctor,
                )
            },
        )

        return ResponseEntity
            .created(URI.create("/api/v1/visits/$visitId/order-items"))
            .body(PlacedOrder(orderId))
    }

    @PostMapping("/order-items/{orderItemId}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelOrderItem(
        @PathVariable orderItemId: Long,
        @Valid @RequestBody request: CancelOrderItemRequest,
    ) {
        orderService.cancelOrderItem(
            orderItemId = orderItemId,
            doctorNo = request.doctorNo,
            reason = request.reason,
        )
    }

    @GetMapping("/visits/{visitId}/order-items")
    fun getOrderItems(@PathVariable visitId: Long): OrderItemListResponse {
        val views = orderItemQueryService.itemsOfVisit(visitId = visitId)
        return OrderItemListResponse(
            items = views.map { view -> OrderItemResponse.from(view = view) },
        )
    }
}
