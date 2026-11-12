package com.htsemr.billing.entity

import com.htsemr.common.Money
import com.htsemr.master.CoverageType

/**
 * 단가와 급여 구분을 확인한 산정 대상 항목 하나. 산정 합계와 산정 내역을 만드는 재료다.
 *
 * 산정을 먼저 저장해야 내역에 붙일 산정 id가 생기므로, 그 사이에 값을 들고 있을 타입이 필요하다.
 * 총액(단가 × 수량)은 여기서 한 번만 계산해 합계와 내역이 같은 값을 쓰게 한다.
 */
internal data class PricedItem(
    val orderItemId: Long,
    val feeItemId: Long,
    val coverageType: CoverageType,
    val unitPrice: Money,
    val quantity: Int,
) {
    init {
        require(quantity >= 1) { "수량은 1 이상이어야 합니다: $quantity" }
    }

    /** 항목 단위로는 끝전을 처리하지 않는다(A-30, A-32). */
    val lineTotal: Money = unitPrice * quantity
}
