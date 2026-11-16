package com.htsemr.reception.external

import com.htsemr.reception.InsuranceType

internal data class EligibilityResponse(
    val insuranceType: InsuranceType,
    val copayRate: Int,
)
