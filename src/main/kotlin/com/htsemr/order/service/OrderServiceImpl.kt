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
import com.htsemr.observability.DomainEventLog
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

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

        val visit : VisitView = visitQueryService.visit(visitId)

        val feeItemDefaults : List<Boolean> = items.map {item ->
            val feeItem = feeQueryService.feeItem(feeItemId = item.feeItemId)

            if(!feeQueryService.hasPriceOn(feeItemId = item.feeItemId, visitDate = visit.visitDate)) {
                throw NoPriceOnVisitDateException(item.feeItemId, visit.visitDate)
            }
            feeItem.executableBeforePayment
        }

        val orderHeader = medicalOrderRepository.save(MedicalOrder(
            visitId = visit.id,

            doctorNo = doctorNo,

            orderedAt = LocalDateTime.now()
        ))

        val orderId = requireNotNull(orderHeader.id)

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

        DomainEventLog.emit(
            "order.placed",
            "orderId" to orderId,
            "visitId" to visit.id,
            "itemCount" to items.size,
            "doctorNo" to doctorNo,
        )
        return orderId
    }

    override fun cancelOrderItem(orderItemId: Long, doctorNo: String, reason: String) {
        val orderItem = orderItemRepository.findByIdOrNull(orderItemId)
            ?: throw OrderItemNotFoundException(orderItemId)

        val statusBefore = orderItem.status
        val cancellation = orderItem.cancel(
            doctorNo = doctorNo,
            reason = reason,
            cancelledAt = LocalDateTime.now(),
        )

        orderItemCancellationRepository.save(cancellation)

        DomainEventLog.emit(
            "orderItem.cancelled",
            "orderItemId" to orderItemId,
            "from" to statusBefore.name,
            "to" to orderItem.status.name,
            "doctorNo" to doctorNo,
        )
    }
}
