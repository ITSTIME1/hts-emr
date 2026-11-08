package com.htsemr.master

import com.htsemr.common.Money
import java.time.LocalDate

/**
 * 수가 조회. master 모듈의 공개 기능이며 조회만 한다.
 *
 * 단가는 항상 **진료일 기준**으로 조회한다(A-06). "지금 단가"를 주는 기능은 일부러 두지 않는다.
 * 지난 내원 건을 수납하거나 재산정할 때도 그 진료일의 단가를 써야 하기 때문이다.
 *
 * @see <a href="../../../../../docs/modules/master.md">master 모듈 경계</a>
 */
interface FeeQueryService {



    /**
     * 수가 항목 하나를 조회한다.
     *
     * - order: 오더를 낼 때 선시행 기본값을 확인한다(A-08).
     * - billing: 산정할 때 급여 구분을 확인한다.
     *
     * @throws FeeItemNotFoundException 수가 항목이 없을 때
     */

    fun feeItem(feeItemId: Long): FeeItemView

    /**
     * 그 진료일에 유효한 단가가 **있는지** 묻는다.
     *
     * order가 오더를 낼 때 쓴다(T-ORD-01 가드). 단가가 없는 것은 정상적인 상황이며
     * (예: 적용 시작일이 아직 오지 않은 새 수가 항목), 그때는 오더를 막는다.
     *
     * [priceOn]과 같은 조건으로 판단한다.
     */
    fun hasPriceOn(feeItemId: Long, visitDate: LocalDate): Boolean

    /**
     * 그 진료일에 유효한 단가를 돌려준다.
     *
     * billing이 산정과 재산정에 쓴다. 산정 대상은 이미 [hasPriceOn]을 통과한 오더 항목이므로,
     * 여기서 단가가 없다는 것은 업무 흐름이 아니라 오류다. 0원으로 넘기지 않고 예외로 멈춘다.
     *
     * @throws FeePriceNotFoundException 그 진료일에 유효한 단가가 없을 때
     */
    fun priceOn(feeItemId: Long, visitDate: LocalDate): Money

}