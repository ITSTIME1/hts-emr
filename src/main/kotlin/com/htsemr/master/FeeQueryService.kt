package com.htsemr.master

import com.htsemr.common.Money
import java.time.LocalDate

interface FeeQueryService {

    fun feeItem(feeItemId: Long): FeeItemView

    fun hasPriceOn(feeItemId: Long, visitDate: LocalDate): Boolean

    fun priceOn(feeItemId: Long, visitDate: LocalDate): Money

}
