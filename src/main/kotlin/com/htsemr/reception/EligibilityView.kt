package com.htsemr.reception

import java.time.LocalDateTime

data class EligibilityView(
    val visitId: Long,
    val seq: Int,
    val insuranceType: InsuranceType,
    val copayRate: Int,
    val recordedAt: LocalDateTime,
)
