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

/**
 * 오더 발행(placeOrder)과 수납 전 오더 취소(cancelOrderItem) 테스트.
 *
 * @ApplicationModuleTest는 order 모듈의 빈만 띄우므로, 다른 모듈의 공개 조회(reception, master)는 대역으로 채운다.
 * 덕분에 "내원 건 없음", "진료일 수가 없음" 같은 상황을 데이터 준비 없이 대역의 응답으로 만든다.
 *
 * 서비스가 자기 트랜잭션을 커밋하므로 클래스에 @Transactional을 붙이지 않는다.
 * 붙이면 서비스의 롤백이 테스트 트랜잭션에 묶여 "실패하면 아무것도 남지 않는다"를 확인할 수 없다.
 * 대신 테스트가 끝날 때마다 직접 지운다.
 */
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

    /**
     * 기본 응답: 내원 건 하나와 수가 항목 둘(선시행 기본값 없음/있음), 진료일 수가는 둘 다 있다.
     * 다른 응답이 필요한 테스트만 덮어쓴다. 덮어쓰지 않은 호출은 Mockito 기본값(false, null)을 돌려준다.
     */
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

    /** 외래 키 방향(취소 기록 → 항목 → 오더)의 반대 순서로 지운다. */
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
            // 진찰료, 주사 두 항목으로 발행한다.
            // 돌려받은 id의 오더가 있고, 그 오더의 항목이 2개인지 본다.
            val items = listOf(
                NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false),
                NewOrderItem(feeItemId = FEE_INJECTION, quantity = 1, approvedByDoctor = false),
            )

            val orderId = orderService.placeOrder(visitId = VISIT_ID, doctorNo = ORDER_DOCTOR, items = items)
            // 오더 헤더가 저장되고, 항목 2건이 저장되는지 확인
            assertThat(medicalOrderRepository.findById(orderId)).isPresent
            assertThat(orderItemRepository.findAll().filter{it.orderId == orderId}).hasSize(2)
        }

        @Test
        fun `확인한 내원 건과 오더 의사를 기록한다`() {
            // 진료 의사(ATTENDING_DOCTOR)와 다른 오더 의사(ORDER_DOCTOR)로 발행한다.
            // 오더의 visitId가 VISIT_ID, doctorNo가 ORDER_DOCTOR인지 본다(진료 의사가 아니다).
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
            // 수량 2로 발행하고, 항목의 quantity와 status를 본다.
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
            // FEE_INJECTION, 의사 지정 없음 → executableBeforePayment true, 근거 FEE_ITEM_DEFAULT
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
            // FEE_CONSULT, 의사 지정 있음 → true, DOCTOR
            orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = ORDER_DOCTOR,
                items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = true)),
                // 의사가 승인을 했으니까 (approvedByDoctor = true)
            )
            val saved = orderItemRepository.findAllByVisitId(VISIT_ID).single()
            assertThat(saved.preExecutionBasis).isEqualTo(PreExecutionBasis.DOCTOR)
        }

        @Test
        fun `기본값과 의사 지정이 둘 다 있으면 기본값이 우선한다`() {
            // FEE_INJECTION, 의사 지정 있음 → true, FEE_ITEM_DEFAULT (A-08)
            orderService.placeOrder(
                visitId = VISIT_ID,
                doctorNo = ORDER_DOCTOR,
                items = listOf(NewOrderItem(feeItemId = FEE_INJECTION, quantity = 1, approvedByDoctor = true)),
                // 의사가 승인을 했으니까 (approvedByDoctor = true)

            )
            val saved = orderItemRepository.findAllByVisitId(VISIT_ID).single()
            assertThat(saved.preExecutionBasis).isEqualTo(PreExecutionBasis.FEE_ITEM_DEFAULT)
        }

        @Test
        fun `둘 다 없으면 승인하지 않고 근거도 비운다`() {
            // FEE_CONSULT, 의사 지정 없음 → false, null
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
            // reception이 던지는 예외를 order가 바꾸지 않고 그대로 올려 보내는지 본다.
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
            // 첫 항목은 정상, 두 번째 항목의 수가 항목이 없다. 하나라도 없으면 오더 전체를 받지 않는다.
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
            // 수가 항목은 있지만, 진료일에 적용할 단가가 없다.
            given(feeQueryService.hasPriceOn(FEE_CONSULT, VISIT_DATE)).willReturn(false)

            val exception = assertThrows<NoPriceOnVisitDateException> {
                orderService.placeOrder(
                    visitId = VISIT_ID,
                    doctorNo = ORDER_DOCTOR,
                    items = listOf(NewOrderItem(feeItemId = FEE_CONSULT, quantity = 1, approvedByDoctor = false)),
                )
            }

            // 진료일은 입력이 아니라 확인한 내원 건에서 꺼낸 값이다.
            assertThat(exception.feeItemId).isEqualTo(FEE_CONSULT)
            assertThat(exception.visitDate).isEqualTo(VISIT_DATE)
            assertNothingSaved()
        }

        @Test
        fun `헤더를 저장한 뒤에 실패해도 오더와 항목이 하나도 남지 않는다`() {
            // 두 번째 항목의 수량을 0으로 준다. 검증 단계(내원 건, 수가)는 통과하고,
            // 헤더 저장 뒤 place()의 require에서 실패한다. 오더 수와 항목 수가 0인지 본다.
            // 헤더는 IDENTITY라 save 때 이미 insert되었으므로, 0건이면 @Transactional이 헤더까지 롤백한 것이다.
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
            // 발행한 항목을 취소하고, 항목 상태와 취소 기록(의사, 사유)을 본다.
            val orderItemId = placeOneItem()

            orderService.cancelOrderItem(orderItemId = orderItemId, doctorNo = ORDER_DOCTOR, reason = "환자 요청")

            // 상태 변경은 save를 부르지 않았지만 더티 체킹으로 저장되었다.
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

            // 예외에 지금 상태가 담겨, 부르는 쪽이 왜 거부되었는지 알 수 있다.
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
            // reason = " " → 예외, 항목은 ORDERED, 취소 기록 0건
            val orderItemId = placeOneItem()

            assertThrows<IllegalArgumentException> {
                orderService.cancelOrderItem(orderItemId = orderItemId, doctorNo = ORDER_DOCTOR, reason = " ")
            }

            assertThat(orderItemRepository.findById(orderItemId).get().status).isEqualTo(OrderItemStatus.ORDERED)
            assertThat(orderItemCancellationRepository.count()).isZero()
        }
    }

    /** 오더도 항목도 남지 않았는지 본다. 거부된 발행은 아무것도 남기지 않아야 한다. */
    private fun assertNothingSaved() {
        assertThat(medicalOrderRepository.count()).isZero()
        assertThat(orderItemRepository.count()).isZero()
    }

    /** 항목 하나로 오더를 내고 그 항목의 id를 돌려준다. 취소 테스트의 준비용. */
    private fun placeOneItem(feeItemId: Long = FEE_CONSULT, approvedByDoctor: Boolean = false): Long {
        orderService.placeOrder(
            visitId = VISIT_ID,
            doctorNo = ORDER_DOCTOR,
            items = listOf(NewOrderItem(feeItemId = feeItemId, quantity = 1, approvedByDoctor = approvedByDoctor)),
        )
        return requireNotNull(orderItemRepository.findAll().single().id)
    }

    companion object {
        // 다른 모듈의 id는 대역이 응답하므로 실제 행이 없어도 된다(모듈 사이 외래 키 없음).
        const val VISIT_ID = 1000L
        const val UNKNOWN_ID = 9999L
        val VISIT_DATE: LocalDate = LocalDate.of(2026, 6, 2)

        const val ATTENDING_DOCTOR = "T-D001"   // 진료 의사(접수 때 배정)
        const val ORDER_DOCTOR = "T-D002"       // 오더 의사(오더를 낸 의사)

        const val FEE_CONSULT = 11L     // 진찰료: 선시행 기본값 없음
        const val FEE_INJECTION = 33L   // 주사: 선시행 기본값 있음
    }
}
