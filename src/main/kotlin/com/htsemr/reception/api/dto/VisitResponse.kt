package com.htsemr.reception.api.dto

import com.htsemr.reception.VisitView
import java.time.LocalDate

internal class VisitResponse(
    val id: Long,
    val patientNo: String,
    val departmentCode: String,
    val doctorNo: String,
    val visitDate: LocalDate,
) {
    companion object {
        fun from(view: VisitView) = VisitResponse(
            id = view.id,
            patientNo = view.patientNo,
            departmentCode = view.departmentCode,
            doctorNo = view.doctorNo,
            visitDate = view.visitDate,
        )
    }
}