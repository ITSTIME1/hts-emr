package com.htsemr.reception

import java.time.LocalDate

/** 같은 환자·진료일·진료과의 내원 건이 이미 있다. 화면은 "이미 접수된 내원 건"으로 안내한다. */
class DuplicateVisitException(
    val patientNo: String,
    val visitDate: LocalDate,
    val departmentCode: String,
    cause: Throwable? = null,
) : RuntimeException("환자 $patientNo 의 $visitDate $departmentCode 내원 건이 이미 있습니다", cause)
