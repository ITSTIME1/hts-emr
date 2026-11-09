package com.htsemr.reception

import java.time.LocalDate

/**
 * 접수(F-01). 내원 건을 만드는 공개 기능.
 *
 * Controller가 호출하지만, 여러 모듈에 걸친 재현 테스트가 실제 흐름대로 내원 건을 만들 수 있도록 공개한다.
 */
interface RegistrationService {

    /**
     * 외부 기관에 자격을 조회한 뒤, 내원 건과 첫 자격 기록을 함께 만든다(A-03, A-04).
     * 환자, 진료과, 진료 의사는 다른 시스템에서 받은 값이다(A-29).
     *
     * @param visitDate 진료일. 시각 없이 날짜만 받는다.
     * @return 만든 내원 건의 id
     * @throws DuplicateVisitException 같은 환자·진료일·진료과의 내원 건이 이미 있을 때
     */
    fun registerVisit(patientNo: String, departmentCode: String, doctorNo: String, visitDate: LocalDate): Long
}
