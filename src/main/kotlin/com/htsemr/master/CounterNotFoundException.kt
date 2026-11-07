package com.htsemr.master

/** 창구가 없다. */
class CounterNotFoundException(val counterId: Long)
    : RuntimeException("창구 $counterId 이 없습니다")
