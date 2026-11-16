package com.htsemr.order

import com.htsemr.TestcontainersConfiguration
import com.htsemr.master.CoverageType
import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.master.FeeItemView
import com.htsemr.master.FeeQueryService
import com.htsemr.order.entity.PreExecutionBasis
import com.htsemr.order.repository.MedicalOrderRepository
import com.htsemr.order.repository.OrderItemCancellationRepository
import com.htsemr.order.repository.OrderItemRepository
import com.htsemr.reception.VisitNotFoundException
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
internal class OrderServiceTest @Autowired constructor(
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
            .willReturn(VisitView(VISIT_ID, "T-P001", "T-IM", ATTENDING_DOCTOR, VISIT_DATE))

        given(feeQueryService.feeItem(FEE_CONSULT))
            .willReturn(FeeItemView(FEE_CONSULT, "T-AA100", "진찰료", CoverageType.COVERED, executableBeforePayment = false))
        given(feeQueryService.feeItem(FEE_INJECTION))
            .willReturn(FeeItemView(FEE_INJECTION, "T-KK010", "주사", CoverageType.COVERED, executableBeforePayment = true))

        given(feeQueryService.hasPriceOn(FEE_CONSULT, VISIT_DATE)).willReturn(true)
        given(feeQueryService.hasPriceOn(FEE_INJECTION, VISIT_DATE)).willReturn(true)
    }

    @AfterEach
    fun cleanUp() {
        orderItemCancellationRepository.deleteAll()
        orderItemRepository.deleteAll()
        medicalOrderRepository.deleteAll()
    }

    @Nested
    inner class `정상 발행은` {

        @Test
        fun `오더 하나와 항목들을 저장하고 오더 id를 돌려준다`() {
            val items = listOf(
                NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false),
                NewOrderItem(feeItemId = FEE_INJECTION, quantity = 1, approvedByDoctor = false),
            )

            val orderId = orderService.placeOrder(visitId = VISIT_ID, doctorNo = ORDER_DOCTOR, items = items)
            assertThat(medicalOrderRepository.findById(orderId)).isPresent
            assertThat(orderItemRepository.findAll().filter{it.orderId == orderId}).hasSize(2)
        }

        @Test
        fun `확인한 내원 건과 오더 의사를 기록한다`() {
            val items = listOf(
                NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false),
                NewOrderItem(feeItemId = FEE_INJECTION, quantity = 1, approvedByDoctor = false),
            )

            val orderId = orderService.placeOrder(visitId = VISIT_ID, doctorNo = ORDER_DOCTOR, items = items)

            assertThat(medicalOrderRepository.findById(orderId).get().doctorNo).isEqualTo(ORDER_DOCTOR)
            assertThat(medicalOrderRepository.findById(orderId).get().doctorNo).isNotEqualTo(ATTENDING_DOCTOR)
        }

        @Test
        fun `항목은 입력 수량 그대로 ORDERED 상태로 저장된다`() {
            val items = listOf(
                NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false),
                NewOrderItem(feeItemId = FEE_INJECTION, quantity = 3, approvedByDoctor = false),
            )

            orderService.placeOrder(visitId = VISIT_ID, doctorNo = ORDER_DOCTOR, items = items)
            val saved = orderItemRepository.findAllByVisitId(VISIT_ID)
            assertThat(saved.map { it.feeItemId to it.quantity })
                .containsExactly(FEE_CONSULT to 1, FEE_INJECTION to 3)

            assertThat(saved.map { it.status })
                .containsOnly(OrderItemStatus.ORDERED)
        }
    }

    @Nested
    inner class `선시행 승인은` {

        @Test
        fun `수가 항목 기본값이면 FEE_ITEM_DEFAULT로 승인한다`() {
            orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = ORDER_DOCTOR,
                items = listOf(NewOrderItem(feeItemId = FEE_INJECTION, quantity = 1, approvedByDoctor = false)),
            )

            val saved = orderItemRepository.findAllByVisitId(VISIT_ID).single()
            assertThat(saved.executableBeforePayment).isTrue()
            assertThat(saved.preExecutionBasis).isEqualTo(PreExecutionBasis.FEE_ITEM_DEFAULT)
        }

        @Test
        fun `의사 지정만 있으면 DOCTOR로 승인한다`() {
            orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = ORDER_DOCTOR,
                items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = true)),
            )
            val saved = orderItemRepository.findAllByVisitId(VISIT_ID).single()
            assertThat(saved.preExecutionBasis).isEqualTo(PreExecutionBasis.DOCTOR)
        }

        @Test
        fun `기본값과 의사 지정이 둘 다 있으면 기본값이 우선한다`() {
            orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = ORDER_DOCTOR,
                items = listOf(NewOrderItem(feeItemId = FEE_INJECTION, quantity = 1, approvedByDoctor = true)),

            )
            val saved = orderItemRepository.findAllByVisitId(VISIT_ID).single()
            assertThat(saved.preExecutionBasis).isEqualTo(PreExecutionBasis.FEE_ITEM_DEFAULT)
        }

        @Test
        fun `둘 다 없으면 승인하지 않고 근거도 비운다`() {
            orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = ORDER_DOCTOR,
                items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false)),
            )

            val saved = orderItemRepository.findAllByVisitId(VISIT_ID).single()
            assertThat(saved.executableBeforePayment).isFalse()
            assertThat(saved.preExecutionBasis).isNull()
        }
    }

    @Nested
    inner class `발행을 거부하면` {

        @Test
        fun `항목 목록이 비어 있으면 IllegalArgumentException이 발생한다`() {
            assertThrows<IllegalArgumentException> {
                orderService.placeOrder(visitId = VISIT_ID, doctorNo = ORDER_DOCTOR, items = emptyList())
            }

            assertNothingSaved()
        }

        @Test
        fun `내원 건이 없으면 VisitNotFoundException이 발생한다`() {
            given(visitQueryService.visit(UNKNOWN_ID)).willThrow(VisitNotFoundException(UNKNOWN_ID))

            val exception = assertThrows<VisitNotFoundException> {
                orderService.placeOrder(
                    visitId = UNKNOWN_ID,
                    doctorNo = ORDER_DOCTOR,
                    items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false)),
                )
            }

            assertThat(exception.visitId).isEqualTo(UNKNOWN_ID)
            assertNothingSaved()
        }

        @Test
        fun `수가 항목이 없으면 FeeItemNotFoundException이 발생한다`() {
            given(feeQueryService.feeItem(UNKNOWN_ID)).willThrow(FeeItemNotFoundException(UNKNOWN_ID))

            val exception = assertThrows<FeeItemNotFoundException> {
                orderService.placeOrder(
                    visitId = VISIT_ID,
                    doctorNo = ORDER_DOCTOR,
                    items = listOf(
                        NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false),
                        NewOrderItem(feeItemId = UNKNOWN_ID, quantity = 1, approvedByDoctor = false),
                    ),
                )
            }

            assertThat(exception.feeItemId).isEqualTo(UNKNOWN_ID)
            assertNothingSaved()
        }

        @Test
        fun `진료일에 유효한 수가가 없으면 NoPriceOnVisitDateException이 발생한다`() {
            given(feeQueryService.hasPriceOn(FEE_CONSULT, VISIT_DATE)).willReturn(false)

            val exception = assertThrows<NoPriceOnVisitDateException> {
                orderService.placeOrder(
                    visitId = VISIT_ID,
                    doctorNo = ORDER_DOCTOR,
                    items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false)),
                )
            }

            assertThat(exception.feeItemId).isEqualTo(FEE_CONSULT)
            assertThat(exception.visitDate).isEqualTo(VISIT_DATE)
            assertNothingSaved()
        }

        @Test
        fun `헤더를 저장한 뒤에 실패해도 오더와 항목이 하나도 남지 않는다`() {
            assertThrows<IllegalArgumentException> {
                orderService.placeOrder(
                    visitId = VISIT_ID,
                    doctorNo = ORDER_DOCTOR,
                    items = listOf(
                        NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false),
                        NewOrderItem(feeItemId = FEE_INJECTION, quantity = 0, approvedByDoctor = false),
                    ),
                )
            }

            assertNothingSaved()
        }
    }

    @Nested
    inner class `오더 취소는` {

        @Test
        fun `ORDERED 항목을 CANCELLED로 바꾸고 취소 기록을 남긴다`() {
            val orderItemId = placeOneItem()

            orderService.cancelOrderItem(orderItemId = orderItemId, doctorNo = ORDER_DOCTOR, reason = "환자 요청")

            assertThat(orderItemRepository.findById(orderItemId).get().status).isEqualTo(OrderItemStatus.CANCELLED)

            val cancellation = orderItemCancellationRepository.findAll().single()
            assertThat(cancellation.orderItemId).isEqualTo(orderItemId)
            assertThat(cancellation.doctorNo).isEqualTo(ORDER_DOCTOR)
            assertThat(cancellation.reason).isEqualTo("환자 요청")
        }

        @Test
        fun `이미 취소된 항목이면 OrderItemNotCancellableException이 발생하고 기록은 하나뿐이다`() {
            val orderItemId = placeOneItem()
            orderService.cancelOrderItem(orderItemId = orderItemId, doctorNo = ORDER_DOCTOR, reason = "환자 요청")

            val exception = assertThrows<OrderItemNotCancellableException> {
                orderService.cancelOrderItem(orderItemId = orderItemId, doctorNo = ORDER_DOCTOR, reason = "다시 취소")
            }

            assertThat(exception.orderItemId).isEqualTo(orderItemId)
            assertThat(exception.status).isEqualTo(OrderItemStatus.CANCELLED)
            assertThat(orderItemCancellationRepository.count()).isEqualTo(1)
        }

        @Test
        fun `없는 항목이면 OrderItemNotFoundException이 발생한다`() {
            val exception = assertThrows<OrderItemNotFoundException> {
                orderService.cancelOrderItem(orderItemId = UNKNOWN_ID, doctorNo = ORDER_DOCTOR, reason = "환자 요청")
            }

            assertThat(exception.orderItemId).isEqualTo(UNKNOWN_ID)
            assertThat(orderItemCancellationRepository.count()).isZero()
        }

        @Test
        fun `사유가 공백뿐이면 IllegalArgumentException이 발생하고 상태는 그대로다`() {
            val orderItemId = placeOneItem()

            assertThrows<IllegalArgumentException> {
                orderService.cancelOrderItem(orderItemId = orderItemId, doctorNo = ORDER_DOCTOR, reason = " ")
            }

            assertThat(orderItemRepository.findById(orderItemId).get().status).isEqualTo(OrderItemStatus.ORDERED)
            assertThat(orderItemCancellationRepository.count()).isZero()
        }
    }

    private fun assertNothingSaved() {
        assertThat(medicalOrderRepository.count()).isZero()
        assertThat(orderItemRepository.count()).isZero()
    }

    private fun placeOneItem(feeItemId: Long = FEE_CONSULT, approvedByDoctor: Boolean = false): Long {
        orderService.placeOrder(
            visitId = VISIT_ID,
            doctorNo = ORDER_DOCTOR,
            items = listOf(NewOrderItem(feeItemId = feeItemId, quantity = 1, approvedByDoctor = approvedByDoctor)),
        )
        return requireNotNull(orderItemRepository.findAll().single().id)
    }

    companion object {
        const val VISIT_ID = 1000L
        const val UNKNOWN_ID = 9999L
        val VISIT_DATE: LocalDate = LocalDate.of(2026, 6, 2)

        const val ATTENDING_DOCTOR = "T-D001"
        const val ORDER_DOCTOR = "T-D002"

        const val FEE_CONSULT = 11L
        const val FEE_INJECTION = 33L
    }
}
