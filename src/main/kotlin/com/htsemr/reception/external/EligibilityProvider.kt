package com.htsemr.reception.external

import java.time.LocalDate

internal interface EligibilityProvider {
    fun inquireEligibility(patientNo: String, visitDate: LocalDate): EligibilityResponse
}
