package com.htsemr.reception

import java.time.LocalDate

/**
 * 내원 건. reception 모듈 밖으로 내보내는 값 객체다.
 *
 * 접수 시각(registeredAt)은 넣지 않는다. 다른 모듈은 진료일(visitDate)로 판단하므로,
 * 접수 시각을 내보내면 둘을 헷갈려 쓸 길이 생긴다.
 */
data class VisitView(
    val id: Long,
    val patientNo: String,
    val departmentCode: String,
    val doctorNo: String,
    val visitDate: LocalDate,
)
