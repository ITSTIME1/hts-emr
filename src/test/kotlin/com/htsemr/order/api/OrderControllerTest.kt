package com.htsemr.order.api

import com.htsemr.api.ApiExceptionHandler
import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.order.NewOrderItem
import com.htsemr.order.NoPriceOnVisitDateException
import com.htsemr.order.OrderItemNotCancellableException
import com.htsemr.order.OrderItemNotFoundException
import com.htsemr.order.OrderItemQueryService
import com.htsemr.order.OrderItemStatus
import com.htsemr.order.OrderItemView
import com.htsemr.order.OrderService
import com.htsemr.reception.VisitNotFoundException
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class OrderControllerTest {

    private val orderService = mock(OrderService::class.java)
    private val orderItemQueryService = mock(OrderItemQueryService::class.java)

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(OrderController(orderService, orderItemQueryService))
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    private fun placeOrder(visitId: Long, body: String) = mockMvc.post("/api/v1/visits/$visitId/orders") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `TC-A-15 정상 오더 발행은 201과 Location, orderId를 돌려준다`() {
        given(
            orderService.placeOrder(57L, "D0001", listOf(NewOrderItem(10L, 2, false))),
        ).willReturn(300L)

        placeOrder(57L, """{"doctorNo":"D0001","orderItems":[{"feeItemId":10,"quantity":2}]}""")
            .andExpect {
                status { isCreated() }
                header { string("Location", endsWith("/api/v1/visits/57/order-items")) }
                jsonPath("$.orderId") { value(300) }
            }
    }

    @Test
    fun `TC-A-16 orderItems가 비어 있으면 400 VALIDATION_FAILED이고 서비스를 부르지 않는다`() {
        placeOrder(57L, """{"doctorNo":"D0001","orderItems":[]}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("orderItems") }
            }

        verifyNoInteractions(orderService)
    }

    @Test
    fun `TC-A-17 quantity가 0이면 400 VALIDATION_FAILED이고 오류 위치에 항목 번호가 들어간다`() {
        placeOrder(
            57L,
            """{"doctorNo":"D0001","orderItems":[{"feeItemId":10,"quantity":1},{"feeItemId":11,"quantity":0}]}""",
        ).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("orderItems[1].quantity") }
            jsonPath("$.errors[0].reason") { value("1 이상이어야 합니다") }
        }

        verifyNoInteractions(orderService)
    }

    @Test
    fun `TC-A-18 doctorNo가 빈 값이면 400 VALIDATION_FAILED`() {
        placeOrder(57L, """{"doctorNo":"","orderItems":[{"feeItemId":10,"quantity":1}]}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("doctorNo") }
            }

        verifyNoInteractions(orderService)
    }

    @Test
    fun `TC-A-19 quantity가 빠지면 400 MALFORMED_REQUEST`() {
        placeOrder(57L, """{"doctorNo":"D0001","orderItems":[{"feeItemId":10}]}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("MALFORMED_REQUEST") }
            }

        verifyNoInteractions(orderService)
    }

    @Test
    fun `TC-A-20 오더 항목 조회는 200과 items 배열을 돌려준다`() {
        given(orderItemQueryService.itemsOfVisit(57L)).willReturn(
            listOf(
                OrderItemView(901L, 300L, 57L, 10L, 2, OrderItemStatus.ORDERED, false),
                OrderItemView(902L, 300L, 57L, 11L, 1, OrderItemStatus.EXECUTED, true),
            ),
        )

        mockMvc.get("/api/v1/visits/57/order-items").andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].id") { value(901) }
            jsonPath("$.items[0].status") { value("ORDERED") }
            jsonPath("$.items[1].executableBeforePayment") { value(true) }
        }
    }

    @Test
    fun `TC-A-21 항목이 없는 내원 건은 200과 빈 배열을 돌려준다`() {
        given(orderItemQueryService.itemsOfVisit(57L)).willReturn(emptyList())

        mockMvc.get("/api/v1/visits/57/order-items").andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
        }
    }

    @Test
    fun `TC-A-22 없는 수가 항목으로 발행하면 404 FEE_ITEM_NOT_FOUND`() {
        given(orderService.placeOrder(57L, "D0001", listOf(NewOrderItem(999L, 1, false))))
            .willThrow(FeeItemNotFoundException(999L))

        placeOrder(57L, """{"doctorNo":"D0001","orderItems":[{"feeItemId":999,"quantity":1}]}""")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("FEE_ITEM_NOT_FOUND") }
            }
    }

    @Test
    fun `TC-A-23 진료일에 유효한 수가가 없으면 422 NO_PRICE_ON_VISIT_DATE`() {
        given(orderService.placeOrder(57L, "D0001", listOf(NewOrderItem(10L, 1, false))))
            .willThrow(NoPriceOnVisitDateException(10L, LocalDate.of(2026, 11, 19)))

        placeOrder(57L, """{"doctorNo":"D0001","orderItems":[{"feeItemId":10,"quantity":1}]}""")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.code") { value("NO_PRICE_ON_VISIT_DATE") }
            }
    }

    @Test
    fun `TC-A-24 없는 접수에 발행하면 404 VISIT_NOT_FOUND`() {
        given(orderService.placeOrder(999L, "D0001", listOf(NewOrderItem(10L, 1, false))))
            .willThrow(VisitNotFoundException(999L))

        placeOrder(999L, """{"doctorNo":"D0001","orderItems":[{"feeItemId":10,"quantity":1}]}""")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("VISIT_NOT_FOUND") }
            }
    }

    private fun cancelOrderItem(orderItemId: Long, body: String) =
        mockMvc.post("/api/v1/order-items/$orderItemId/cancellation") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    @Test
    fun `TC-A-25 정상 취소는 204이고 본문이 없다`() {
        cancelOrderItem(901L, """{"doctorNo":"D0001","reason":"환자 거부"}""")
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }

        verify(orderService).cancelOrderItem(901L, "D0001", "환자 거부")
    }

    @Test
    fun `TC-A-26 사유가 비어 있으면 400 VALIDATION_FAILED이고 서비스를 부르지 않는다`() {
        cancelOrderItem(901L, """{"doctorNo":"D0001","reason":" "}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("reason") }
            }

        verifyNoInteractions(orderService)
    }

    @Test
    fun `TC-A-27 사유가 201자이면 400 VALIDATION_FAILED`() {
        val tooLong = "가".repeat(201)

        cancelOrderItem(901L, """{"doctorNo":"D0001","reason":"$tooLong"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("reason") }
                jsonPath("$.errors[0].reason") { value("최대 200자입니다") }
            }

        verifyNoInteractions(orderService)
    }

    @Test
    fun `TC-A-28 없는 오더 항목을 취소하면 404 ORDER_ITEM_NOT_FOUND`() {
        willThrow(OrderItemNotFoundException(999L))
            .given(orderService).cancelOrderItem(999L, "D0001", "환자 거부")

        cancelOrderItem(999L, """{"doctorNo":"D0001","reason":"환자 거부"}""")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("ORDER_ITEM_NOT_FOUND") }
            }
    }

    @Test
    fun `TC-A-29 취소할 수 없는 상태이면 409 ORDER_ITEM_NOT_CANCELLABLE이고 현재 상태를 알려 준다`() {
        willThrow(OrderItemNotCancellableException(901L, OrderItemStatus.EXECUTED))
            .given(orderService).cancelOrderItem(901L, "D0001", "환자 거부")

        cancelOrderItem(901L, """{"doctorNo":"D0001","reason":"환자 거부"}""")
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("ORDER_ITEM_NOT_CANCELLABLE") }
                jsonPath("$.currentStatus") { value("EXECUTED") }
                jsonPath("$.status") { value(409) }
            }
    }
}
