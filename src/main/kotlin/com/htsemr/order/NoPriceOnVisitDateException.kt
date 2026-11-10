package com.htsemr.order

import java.time.LocalDate

class NoPriceOnVisitDateException(val feeItemId : Long, val visitDate : LocalDate)
    :RuntimeException("수가 항목 $feeItemId 은 진료일 $visitDate 에 유효한 수가가 없습니다")
{}