package com.htsemr.reception.external

import com.htsemr.reception.InsuranceType
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 모의 외부 기관. 누구를 조회하든 건강보험, 본인부담률 50%를 돌려준다.
 * 50%는 종합병원 외래 본인부담률을 예시로 쓴 값이다.
 *
 * 응답을 환자 번호 규칙 등으로 나누지 않는다. 다른 응답이 필요한 테스트는 이 빈을 바꿔 끼워,
 * 테스트 코드만 보고도 어떤 자격이 나오는지 알 수 있게 한다.
 */
@Component
internal class FakeEligibilityProvider : EligibilityProvider {

    override fun inquireEligibility(patientNo: String, visitDate: LocalDate) =
        EligibilityResponse(insuranceType = InsuranceType.NATIONAL_HEALTH, copayRate = 50)
}
