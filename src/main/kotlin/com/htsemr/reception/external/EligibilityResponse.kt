package com.htsemr.reception.external

import com.htsemr.reception.InsuranceType

/**
 * 외부 기관이 알려 준 자격. 내원 건에 자격 기록으로 남긴다.
 *
 * 형식은 실제 공단 형식이 아니며, 가정한 형식이다.
 * 따라서, 만약 공단 형식으로 바뀐다면 해당 형식을 바꾸면
 */
internal data class EligibilityResponse(
    val insuranceType: InsuranceType,
    val copayRate: Int,
)
