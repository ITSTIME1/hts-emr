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

/**
 * 오더 항목 조회(itemsOfVisit, statusesOf) 테스트.
 *
 * 오더는 실제 흐름대로 placeOrder로 만든다. placeOrder가 자기 트랜잭션을 커밋하므로
 * @Transactional 롤백에 기대지 않고 테스트가 끝날 때마다 직접 지운다.
 * 다른 모듈의 공개 조회(reception, master)는 대역으로 채운다.
 */
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

    /** 내원 건 둘(다른 내원 건의 항목이 섞이지 않는지 보기 위해)과 수가 항목 하나. */
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

    /** 외래 키 방향(취소 기록 → 항목 → 오더)의 반대 순서로 지운다. */
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
            // 같은 내원 건에 오더를 두 번 낸다(항목 2개 + 1개). 결과가 3개이고 id 오름차순인지 본다.
            val firstOrderItemIds = placeItems(count = 2)
            val secondOrderItemIds = placeItems(count = 1)

            val result = orderItemQueryService.itemsOfVisit(VISIT_ID)

            assertThat(result.map { it.id }).containsExactlyElementsOf(firstOrderItemIds + secondOrderItemIds)
        }

        @Test
        fun `항목의 값을 그대로 보여 주고 내원 건 id를 함께 담는다`() {
            // OrderItemView의 orderId, visitId, feeItemId, quantity, status, executableBeforePayment를 본다.
            // 수량은 기본값(1)과 구분되도록 2로 준다.
            val orderId = orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = "T-D001",
                items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 2, approvedByDoctor = false)),
            )

            val view = orderItemQueryService.itemsOfVisit(VISIT_ID).single()

            assertThat(view.orderId).isEqualTo(orderId)
            assertThat(view.visitId).isEqualTo(VISIT_ID)   // 항목에는 없고 오더에 있는 값
            assertThat(view.feeItemId).isEqualTo(FEE_CONSULT)
            assertThat(view.quantity).isEqualTo(2)
            assertThat(view.status).isEqualTo(OrderItemStatus.ORDERED)
            assertThat(view.executableBeforePayment).isFalse()
        }

        @Test
        fun `다른 내원 건의 항목은 포함하지 않는다`() {
            // VISIT_ID와 OTHER_VISIT_ID에 각각 발행하고, VISIT_ID로 조회한 결과에 다른 쪽 항목이 없는지 본다.
            val mine = placeItems(visitId = VISIT_ID)
            placeItems(visitId = OTHER_VISIT_ID)

            val result = orderItemQueryService.itemsOfVisit(VISIT_ID)

            assertThat(result.map { it.id }).containsExactlyElementsOf(mine)
        }

        @Test
        fun `취소된 항목도 지금 상태와 함께 포함한다`() {
            // 하나를 취소한 뒤 조회하면 그 항목이 CANCELLED로 나오는지 본다(효력 판단은 쓰는 쪽이 한다).
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
            // 두 항목 중 하나를 취소하고 조회한다. {첫째=ORDERED, 둘째=CANCELLED}인지 본다.
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
            // 있는 id와 UNKNOWN_ID를 함께 넘긴다. 예외의 orderItemId가 UNKNOWN_ID인지도 본다.
            // 있는 항목만 담아 돌려주면, 부르는 쪽이 모르는 항목을 조용히 통과시킬 수 있다.
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

    /** 진찰료 항목 count개로 오더를 내고, 새로 생긴 항목 id들을 id 순서로 돌려준다. */
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
