package com.htsemr.reception.external

import java.time.LocalDate

/**
 * 외부 기관 자격 조회.
 *
 * 이 프로젝트에는 실제 기관이 없으므로 모의 구현([FakeEligibilityProvider])이 응답한다.
 * 조회 실패나 지연 같은 시나리오는 테스트에서 이 빈을 바꿔 끼워 재현한다.
 *
 * 나중에는 이 부분이 외부 기관 조회 구현체가 된다.
 */
internal interface EligibilityProvider {
    fun inquireEligibility(patientNo: String, visitDate: LocalDate): EligibilityResponse
}
