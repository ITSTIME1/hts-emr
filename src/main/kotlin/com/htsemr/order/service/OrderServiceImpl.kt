package com.htsemr.order.service

import com.htsemr.master.FeeQueryService
import com.htsemr.order.NewOrderItem
import com.htsemr.order.NoPriceOnVisitDateException
import com.htsemr.order.OrderItemNotFoundException
import com.htsemr.order.OrderService
import com.htsemr.order.entity.MedicalOrder
import com.htsemr.order.entity.OrderItem
import com.htsemr.order.repository.MedicalOrderRepository
import com.htsemr.order.repository.OrderItemCancellationRepository
import com.htsemr.order.repository.OrderItemRepository
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 오더 발행 구현.
 *
 * [internal인 이유]
 * 클래스가 public이면 주 생성자도 public이 된다. 그런데 생성자가 받는 리포지토리는 internal이라
 * 모듈 밖에서는 넘길 수 없다. 부를 수 없는 생성자를 공개하는 모순이라 컴파일러가 막는다.
 * 바깥에는 인터페이스 OrderService만 공개하고, 구현은 숨긴다.
 */
@Service
@Transactional
internal class OrderServiceImpl(
    private val visitQueryService : VisitQueryService,
    private val feeQueryService : FeeQueryService,
    private val medicalOrderRepository : MedicalOrderRepository,
    private val orderItemRepository : OrderItemRepository,
    private val orderItemCancellationRepository : OrderItemCancellationRepository,
) : OrderService {

    override fun placeOrder(
        visitId: Long,
        doctorNo: String,
        items: List<NewOrderItem>
    ): Long {
        require(items.isNotEmpty()) { "오더 항목이 없습니다" }

        // 1. 오더를 낼 내원 건을 확인한다.
        //    없으면 reception이 VisitNotFoundException을 던진다.
        val visit : VisitView = visitQueryService.visit(visitId)

        // 2. 항목마다 수가 항목을 확인하고, 선시행 기본값을 모은다.
        //    결과는 items와 같은 순서, 같은 길이다(map은 순서와 길이를 유지한다).
        val feeItemDefaults : List<Boolean> = items.map {item ->
            // 수가 항목이 없으면 master가 FeeItemNotFoundException을 던진다.
            val feeItem = feeQueryService.feeItem(feeItemId = item.feeItemId)

            // 진료일에 적용할 수가가 없으면 산정할 수 없으므로 오더를 받지 않는다(T-ORD-01).
            if(!feeQueryService.hasPriceOn(feeItemId = item.feeItemId, visitDate = visit.visitDate)) {
                throw NoPriceOnVisitDateException(item.feeItemId, visit.visitDate)
            }
            feeItem.executableBeforePayment
        }

        // 3. 오더 헤더를 저장한다.
        val orderHeader = medicalOrderRepository.save(MedicalOrder(
            // 파라미터 visitId가 아니라 1에서 확인한 visit.id를 쓴다. 값은 같지만,
            // 이렇게 해야 1의 확인을 지우면 이 줄이 컴파일되지 않아 확인과 저장이 코드로 묶인다.
            visitId = visit.id,

            // visit.doctorNo(진료 의사, 접수 때 배정)가 아니라 파라미터(오더 의사, 지금 오더를 낸 의사)를 쓴다.
            // 전공의 오더, 대진, 협진처럼 둘은 다를 수 있다. 진료 의사를 넣으면
            // 오더를 내지 않은 의사가 낸 것으로 기록된다.
            doctorNo = doctorNo,

            // 오더 시각은 서버가 이 요청을 처리하는 시각으로 정한다.
            // 요청을 받은 시각과의 차이는 ms 수준이라, 언제 오더가 났는지라는 사실은 달라지지 않는다.
            orderedAt = LocalDateTime.now()
        ))

        // IDENTITY라서 save 시점에 insert가 나가 id가 채워진다. 그래서 여기서 null이면 로직의 문제다.
        // requireNotNull은 Long?을 Long으로 바꿔 준다.
        val orderId = requireNotNull(orderHeader.id)

        // 4. 오더 항목을 저장한다.
        //    zip으로 입력 항목과 2의 선시행 기본값을 같은 위치끼리 짝짓고, place로 OrderItem을 만든다.
        //    선시행 승인과 근거는 place가 정한다. saveAll은 항목마다 save를 부른다.
        orderItemRepository.saveAll(
            items.zip(feeItemDefaults) { item, feeItemDefault ->
                OrderItem.place(
                    orderId = orderId,
                    feeItemId = item.feeItemId,
                    quantity = item.quantity,
                    approvedByDoctor = item.approvedByDoctor,
                    feeItemDefault = feeItemDefault,
                )
            }
        )
        return orderId
    }

    override fun cancelOrderItem(orderItemId: Long, doctorNo: String, reason: String) {
        // 1. 취소할 오더 항목을 찾는다.
        val orderItem = orderItemRepository.findByIdOrNull(orderItemId)
            ?: throw OrderItemNotFoundException(orderItemId)

        // 2. 항목에게 취소를 시킨다. 사유 확인, 상태 확인, 상태 변경은 엔티티가 하고, 취소 기록을 돌려준다.
        //    ORDERED가 아니면 OrderItemNotCancellableException이 그대로 올라간다.
        val cancellation = orderItem.cancel(
            doctorNo = doctorNo,
            reason = reason,
            cancelledAt = LocalDateTime.now(),
        )

        // 3. 취소 기록을 저장한다.
        //    상태 변경은 저장을 부르지 않아도 된다. 1에서 읽은 항목은 영속성 컨텍스트가 관리하므로
        //    커밋할 때 더티 체킹이 update를 보낸다. 취소 기록은 새로 만든 객체라 직접 저장해야 한다.
        //    같은 항목을 동시에 취소하면 uk_order_cancel_item이 두 번째 insert를 막고 전체가 롤백된다.
        orderItemCancellationRepository.save(cancellation)
    }
}
