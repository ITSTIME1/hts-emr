package com.htsemr.master

class CounterNotFoundException(val counterId: Long)
    : RuntimeException("창구 $counterId 이 없습니다")
