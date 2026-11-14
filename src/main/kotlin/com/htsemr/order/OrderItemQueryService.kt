package com.htsemr.order

/**
 * 오더 항목 조회. order 모듈의 공개 기능이며 조회만 한다.
 *
 * - payment: 수납하지 않은 항목을 골라 산정 대상으로 넘기고(A-02),
 *   수납 완료 직전에 항목이 아직 효력이 있는지 확인한다(T-PAY-03).
 */
interface OrderItemQueryService {

    /**
     * 내원 건의 오더 항목 전부. 조회 시점의 상태이며, 항목 id 순서다.
     * 오더가 없으면 빈 목록이다. 내원 건이 있는지는 확인하지 않는다.
     */
    fun itemsOfVisit(visitId: Long): List<OrderItemView>

    /**
     * 오더 항목별 지금 상태. 효력이 있는지는 [OrderItemStatus.isEffective]로 판단한다(INV-04).
     *
     * @throws OrderItemNotFoundException 하나라도 없는 항목이 있을 때.
     *   모르는 항목을 결과에서 빼고 돌려주면, 부르는 쪽이 그 항목을 조용히 통과시킬 수 있다.
     */
    fun statusesOf(orderItemIds: Collection<Long>): Map<Long, OrderItemStatus>
}
