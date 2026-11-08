package com.htsemr.master.mapping

import com.htsemr.master.CounterView
import com.htsemr.master.entity.Counter

internal fun Counter.toCounterView() = CounterView(
    id = requireNotNull(id) { "저장되지 않은 창구" },
    code = code,
    name = name,
)
