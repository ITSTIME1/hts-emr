package com.htsemr.master

import java.time.LocalDate

class FeePriceNotFoundException(val feeItemId: Long, val visitDate: LocalDate)
    : RuntimeException("수가 항목 $feeItemId 에 진료일 $visitDate 기준 유효한 단가가 없습니다")
