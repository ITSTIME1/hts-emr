package com.htsemr.reception.mapping

import com.htsemr.reception.EligibilityView
import com.htsemr.reception.entity.Eligibility

internal fun Eligibility.toEligibilityView() = EligibilityView(
    visitId = visitId,
    seq = seq,
    insuranceType = insuranceType,
    copayRate = copayRate,
    recordedAt = recordedAt,
)
