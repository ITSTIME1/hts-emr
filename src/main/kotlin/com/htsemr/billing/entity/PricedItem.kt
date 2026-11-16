package com.htsemr.billing.entity

import com.htsemr.common.Money
import com.htsemr.master.CoverageType

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

    val lineTotal: Money = unitPrice * quantity
}
