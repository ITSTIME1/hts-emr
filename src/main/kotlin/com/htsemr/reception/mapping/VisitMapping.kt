package com.htsemr.reception.mapping

import com.htsemr.reception.VisitView
import com.htsemr.reception.entity.Visit

internal fun Visit.toVisitView() = VisitView(
    id = requireNotNull(id) { "저장되지 않은 내원 건" },
    patientNo = patientNo,
    departmentCode = departmentCode,
    doctorNo = doctorNo,
    visitDate = visitDate,
)
