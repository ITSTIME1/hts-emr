package com.htsemr.reception.external

import com.htsemr.reception.InsuranceType
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
internal class FakeEligibilityProvider : EligibilityProvider {

    override fun inquireEligibility(patientNo: String, visitDate: LocalDate) =
        EligibilityResponse(insuranceType = InsuranceType.NATIONAL_HEALTH, copayRate = 50)
}
