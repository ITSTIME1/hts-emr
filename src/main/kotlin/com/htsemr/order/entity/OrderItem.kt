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

    /**
     * 지금 상태. cancel() 같은 전이 메서드로만 바꾼다.
     *
     * private set이 아니라 protected set인 이유: allOpen이 @Entity의 프로퍼티를 open으로 만드는데,
     * Kotlin은 open 프로퍼티에 private set을 허용하지 않는다. 모듈 밖에서 막히는 효과는 같다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderItemStatus = OrderItemStatus.ORDERED
        protected set

    /** 수납 전 오더 취소(T-ORD-04). 상태를 바꾸고, 함께 저장할 취소 기록을 돌려준다. */
    fun cancel(doctorNo : String, reason : String, cancelledAt : LocalDateTime) : OrderItemCancellation {
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
        /**
         * 오더 발행(T-ORD-01). 선시행 승인과 그 근거를 여기서 정한다.
         *
         * 생성자를 막고 이 메서드로만 만드는 이유: 근거를 먼저 정하고 승인 여부를 거기서 계산하므로,
         * "승인인데 근거 없음" 같은 어긋난 객체가 만들어질 길이 없다(DB의 ck_order_item_pre_exec_basis와 같은 규칙).
         */
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