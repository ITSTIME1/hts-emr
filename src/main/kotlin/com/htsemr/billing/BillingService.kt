package com.htsemr.billing

import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.master.FeePriceNotFoundException
import com.htsemr.reception.VisitNotFoundException

/**
 * 진료비 산정. billing 모듈의 공개 기능이다.
 *
 * - payment: 수납하지 않은 오더 항목을 골라 산정을 만들고, 그 본인부담 합계로 수납을 시작한다(F-03, F-04).
 */
interface BillingService {

    /**
     * 산정을 만들고 id를 돌려준다(F-03).
     *
     * 자격은 지금 시점의 가장 나중 기록, 단가는 진료일 기준 수가(A-06)로 계산한다.
     * 끝전은 산정 합계 단위로 법정 기준에 따라 처리한다(A-30, A-31). 만든 산정은 바뀌지 않는다.
     *
     * @throws IllegalArgumentException 대상이 비었거나, 같은 오더 항목이 두 번 들어 있거나, 수량이 1보다 작을 때
     * @throws VisitNotFoundException 내원 건이 없을 때
     * @throws FeeItemNotFoundException 수가 항목이 없을 때
     * @throws FeePriceNotFoundException 진료일에 유효한 단가가 없을 때
     */
    fun calculate(visitId: Long, targets: List<BillingTarget>): Long
}
