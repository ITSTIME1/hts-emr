package com.htsemr.reception

/**
 * 보험 유형. 자격의 종류로, 건강보험과 일반 두 가지만 다룬다(A-04).
 * 일반(비보험) 자격은 급여 항목도 전액 본인부담이므로 본인부담률을 100으로 기록한다.
 *
 * billing이 자격을 받아 산정하므로 공개 패키지에 둔다.
 */
enum class InsuranceType { NATIONAL_HEALTH, UNINSURED }
