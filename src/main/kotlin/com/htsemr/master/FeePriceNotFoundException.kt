package com.htsemr.master

import java.time.LocalDate

/** 진료일에 유효한 단가가 없다. 오더 단계의 확인(hasPriceOn)이 빠졌거나 데이터가 잘못된 경우다. */
class FeePriceNotFoundException(val feeItemId: Long, val visitDate: LocalDate)
    : RuntimeException("수가 항목 $feeItemId 에 진료일 $visitDate 기준 유효한 단가가 없습니다")