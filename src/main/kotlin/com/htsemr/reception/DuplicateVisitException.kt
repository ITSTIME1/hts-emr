package com.htsemr.reception

import java.time.LocalDate

class DuplicateVisitException(
    val patientNo: String,
    val visitDate: LocalDate,
    val departmentCode: String,
    cause: Throwable? = null,
) : RuntimeException("환자 $patientNo 의 $visitDate $departmentCode 내원 건이 이미 있습니다", cause)
