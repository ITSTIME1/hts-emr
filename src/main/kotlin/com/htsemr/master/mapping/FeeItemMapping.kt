package com.htsemr.master.mapping

import com.htsemr.master.FeeItemView
import com.htsemr.master.entity.FeeItem

internal fun FeeItem.toFeeItemView() = FeeItemView(
    id = requireNotNull(id) { "저장되지 않은 수가 항목" },
    code = code,
    name = name,
    coverageType = coverageType,
    executableBeforePayment = executableBeforePayment,
)
