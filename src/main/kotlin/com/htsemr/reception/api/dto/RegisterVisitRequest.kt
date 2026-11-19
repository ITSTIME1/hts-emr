package com.htsemr.reception.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

internal data class RegisterVisitRequest (
    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 20, message = "최대 20자입니다")
    val patientNo: String,

    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 10, message = "최대 10자입니다")
    val departmentCode : String,

    @field:NotBlank(message = "필수입니다")
    @field:Size(max = 20, message = "최대 20자입니다")
    val doctorNo : String,
    val visitDate : LocalDate
)
