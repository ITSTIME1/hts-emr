package com.htsemr.reception

import java.time.LocalDate

data class VisitView(
    val id: Long,
    val patientNo: String,
    val departmentCode: String,
    val doctorNo: String,
    val visitDate: LocalDate,
)
