package com.htsemr.master

data class FeeItemView(
    val id: Long,
    val code: String,
    val name: String,
    val coverageType: CoverageType,
    val executableBeforePayment: Boolean,
)