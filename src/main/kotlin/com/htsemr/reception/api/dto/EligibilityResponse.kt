package com.htsemr.reception.api.dto

import com.htsemr.reception.EligibilityView
import com.htsemr.reception.InsuranceType
import java.time.LocalDateTime

internal data class EligibilityResponse(
    val visitId: Long,
    val seq: Int,
    val insuranceType: InsuranceType,
    val copayRate: Int,
    val recordedAt: LocalDateTime,
) {
    companion object {
        fun from(view: EligibilityView) = EligibilityResponse(
            visitId = view.visitId,
            seq = view.seq,
            insuranceType = view.insuranceType,
            copayRate = view.copayRate,
            recordedAt = view.recordedAt,
        )
    }
}
