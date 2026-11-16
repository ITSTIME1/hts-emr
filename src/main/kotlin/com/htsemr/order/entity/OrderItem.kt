package com.htsemr.order.entity

import com.htsemr.order.OrderItemNotCancellableException
import com.htsemr.order.OrderItemStatus
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.NumericBooleanConverter
import java.time.LocalDateTime

@Entity
@Table(name = "order_item")
internal class OrderItem private constructor(
    @Column(nullable = false) val orderId: Long,
    @Column(nullable = false) val feeItemId: Long,
    @Column(nullable = false) val quantity: Int,

    @Convert(converter = NumericBooleanConverter::class)
    @Column(nullable = false)
    val executableBeforePayment: Boolean,

    @Enumerated(EnumType.STRING)
    val preExecutionBasis: PreExecutionBasis?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderItemStatus = OrderItemStatus.ORDERED
        protected set

    fun cancel(doctorNo : String, reason : String, cancelledAt : LocalDateTime) : OrderItemCancellation {
        require(reason.isNotBlank()) { "취소 사유가 없습니다" }
        val orderItemId = requireNotNull(id) { "저장되지 않은 오더 항목" }
        if(status != OrderItemStatus.ORDERED) {
            throw OrderItemNotCancellableException(orderItemId, status)
        }
        status = OrderItemStatus.CANCELLED
        return OrderItemCancellation(
            orderItemId = orderItemId,
            doctorNo = doctorNo,
            reason = reason,
            cancelledAt = cancelledAt,
        )
    }

    companion object {
        fun place(orderId: Long, feeItemId: Long, quantity: Int, approvedByDoctor: Boolean, feeItemDefault: Boolean): OrderItem {
            require(quantity >= 1) { "수량은 1 이상이어야 합니다: $quantity" }
            val basis = when {
                feeItemDefault -> PreExecutionBasis.FEE_ITEM_DEFAULT
                approvedByDoctor -> PreExecutionBasis.DOCTOR
                else -> null
            }
            return OrderItem(
                orderId = orderId,
                feeItemId = feeItemId,
                quantity = quantity,
                executableBeforePayment = basis != null,
                preExecutionBasis = basis,
            )
        }
    }
}
