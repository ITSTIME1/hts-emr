package com.htsemr.order

import com.htsemr.TestcontainersConfiguration
import com.htsemr.master.CoverageType
import com.htsemr.master.FeeItemView
import com.htsemr.master.FeeQueryService
import com.htsemr.order.repository.MedicalOrderRepository
import com.htsemr.order.repository.OrderItemCancellationRepository
import com.htsemr.order.repository.OrderItemRepository
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
internal class OrderItemQueryServiceTest @Autowired constructor(
    private val orderItemQueryService: OrderItemQueryService,
    private val orderService: OrderService,
    private val medicalOrderRepository: MedicalOrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderItemCancellationRepository: OrderItemCancellationRepository,
) {

    @MockitoBean
    private lateinit var visitQueryService: VisitQueryService

    @MockitoBean
    private lateinit var feeQueryService: FeeQueryService

    @BeforeEach
    fun stubOtherModules() {
        given(visitQueryService.visit(VISIT_ID))
            .willReturn(VisitView(VISIT_ID, "T-P001", "T-IM", "T-D001", VISIT_DATE))
        given(visitQueryService.visit(OTHER_VISIT_ID))
            .willReturn(VisitView(OTHER_VISIT_ID, "T-P002", "T-IM", "T-D001", VISIT_DATE))

        given(feeQueryService.feeItem(FEE_CONSULT))
            .willReturn(FeeItemView(FEE_CONSULT, "T-AA100", "진찰료", CoverageType.COVERED, executableBeforePayment = false))
        given(feeQueryService.hasPriceOn(FEE_CONSULT, VISIT_DATE)).willReturn(true)
    }

    @AfterEach
    fun cleanUp() {
        orderItemCancellationRepository.deleteAll()
        orderItemRepository.deleteAll()
        medicalOrderRepository.deleteAll()
    }

    @Nested
    inner class `itemsOfVisit은` {

        @Test
        fun `내원 건의 모든 오더의 항목을 id 순서로 돌려준다`() {
            val firstOrderItemIds = placeItems(count = 2)
            val secondOrderItemIds = placeItems(count = 1)

            val result = orderItemQueryService.itemsOfVisit(VISIT_ID)

            assertThat(result.map { it.id }).containsExactlyElementsOf(firstOrderItemIds + secondOrderItemIds)
        }

        @Test
        fun `항목의 값을 그대로 보여 주고 내원 건 id를 함께 담는다`() {
            val orderId = orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = "T-D001",
                items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 2, approvedByDoctor = false)),
            )

            val view = orderItemQueryService.itemsOfVisit(VISIT_ID).single()

            assertThat(view.orderId).isEqualTo(orderId)
            assertThat(view.visitId).isEqualTo(VISIT_ID)
            assertThat(view.feeItemId).isEqualTo(FEE_CONSULT)
            assertThat(view.quantity).isEqualTo(2)
            assertThat(view.status).isEqualTo(OrderItemStatus.ORDERED)
            assertThat(view.executableBeforePayment).isFalse()
        }

        @Test
        fun `다른 내원 건의 항목은 포함하지 않는다`() {
            val mine = placeItems(visitId = VISIT_ID)
            placeItems(visitId = OTHER_VISIT_ID)

            val result = orderItemQueryService.itemsOfVisit(VISIT_ID)

            assertThat(result.map { it.id }).containsExactlyElementsOf(mine)
        }

        @Test
        fun `취소된 항목도 지금 상태와 함께 포함한다`() {
            val (cancelled, remaining) = placeItems(count = 2)
            orderService.cancelOrderItem(orderItemId = cancelled, doctorNo = "T-D001", reason = "환자 요청")

            val result = orderItemQueryService.itemsOfVisit(VISIT_ID)

            assertThat(result.map { it.id to it.status }).containsExactly(
                cancelled to OrderItemStatus.CANCELLED,
                remaining to OrderItemStatus.ORDERED,
            )
        }

        @Test
        fun `오더가 없으면 빈 목록을 돌려준다`() {
            assertThat(orderItemQueryService.itemsOfVisit(VISIT_ID)).isEmpty()
        }
    }

    @Nested
    inner class `statusesOf는` {

        @Test
        fun `항목별 지금 상태를 돌려준다`() {
            val (first, second) = placeItems(count = 2)
            orderService.cancelOrderItem(orderItemId = second, doctorNo = "T-D001", reason = "환자 요청")

            val statuses = orderItemQueryService.statusesOf(listOf(first, second))

            assertThat(statuses).isEqualTo(
                mapOf(first to OrderItemStatus.ORDERED, second to OrderItemStatus.CANCELLED)
            )
        }

        @Test
        fun `같은 id를 여러 번 넘겨도 한 번만 담는다`() {
            val orderItemId = placeItems().single()

            val statuses = orderItemQueryService.statusesOf(listOf(orderItemId, orderItemId))

            assertThat(statuses).isEqualTo(mapOf(orderItemId to OrderItemStatus.ORDERED))
        }

        @Test
        fun `없는 id가 하나라도 섞이면 OrderItemNotFoundException이 발생한다`() {
            val orderItemId = placeItems().single()

            val exception = assertThrows<OrderItemNotFoundException> {
                orderItemQueryService.statusesOf(listOf(orderItemId, UNKNOWN_ID))
            }

            assertThat(exception.orderItemId).isEqualTo(UNKNOWN_ID)
        }

        @Test
        fun `빈 목록을 넘기면 빈 Map을 돌려준다`() {
            assertThat(orderItemQueryService.statusesOf(emptyList())).isEmpty()
        }
    }

    private fun placeItems(visitId: Long = VISIT_ID, count: Int = 1): List<Long> {
        val orderId = orderService.placeOrder(
            visitId = visitId,
            doctorNo = "T-D001",
            items = List(count) { NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false) },
        )
        return orderItemRepository.findAll()
            .filter { it.orderId == orderId }
            .mapNotNull { it.id }
            .sorted()
    }

    companion object {
        const val VISIT_ID = 1000L
        const val OTHER_VISIT_ID = 2000L
        const val UNKNOWN_ID = 9999L
        val VISIT_DATE: LocalDate = LocalDate.of(2026, 6, 2)

        const val FEE_CONSULT = 11L
    }
}
