package com.htsemr.reception

import java.time.LocalDate

interface RegistrationService {

    fun registerVisit(patientNo: String, departmentCode: String, doctorNo: String, visitDate: LocalDate): Long
}
