package com.htsemr.reception

interface EligibilityQueryService {

    fun currentEligibility(visitId: Long): EligibilityView
}
