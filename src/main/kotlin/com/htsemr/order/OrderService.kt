package com.htsemr.order

import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.reception.VisitNotFoundException

/**
 * 오더 발행과 취소. order 모듈의 공개 기능이며 오더 항목의 상태를 바꾼다.
 *
 * 의사가 진료 중에 쓴다(F-02). 의사 번호는 다른 시스템에서 받은 값이라 확인하지 않는다(A-29).
 */
interface OrderService {

    /**
     * 내원 건에 오더를 낸다(T-ORD-01). 오더와 항목은 함께 저장되거나 함께 저장되지 않는다.
     *
     * 선시행 승인은 이때 정해지며, 수가 항목 기본값이 의사 지정보다 우선한다(A-01, A-08).
     *
     * @return 오더 id
     * @throws IllegalArgumentException 항목 목록이 비어 있거나 수량이 1보다 작을 때
     * @throws VisitNotFoundException 내원 건이 없을 때
     * @throws FeeItemNotFoundException 수가 항목이 없을 때
     * @throws NoPriceOnVisitDateException 진료일에 유효한 수가가 없을 때
     */
    fun placeOrder(visitId: Long, doctorNo: String, items: List<NewOrderItem>): Long

    /**
     * 수납 전 오더 취소(T-ORD-04). ORDERED인 항목만 CANCELLED로 바꾸고 오더 취소 기록을 남긴다.
     *
     * 슬라이스 1에서는 수납 건의 사실을 확인하지 않는다(order.md 7절).
     *
     * @throws IllegalArgumentException 사유가 비어 있을 때
     * @throws OrderItemNotFoundException 오더 항목이 없을 때
     * @throws OrderItemNotCancellableException ORDERED가 아닐 때
     */
    fun cancelOrderItem(orderItemId: Long, doctorNo: String, reason: String)
}
