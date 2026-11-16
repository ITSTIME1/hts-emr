package com.htsemr.master

class FeeItemNotFoundException(val feeItemId: Long)
    : RuntimeException("수가 항목 $feeItemId 이 없습니다")
