package com.htsemr.master

/** 수가 항목이 없다. */
class FeeItemNotFoundException(val feeItemId: Long)
    : RuntimeException("수가 항목 $feeItemId 이 없습니다")