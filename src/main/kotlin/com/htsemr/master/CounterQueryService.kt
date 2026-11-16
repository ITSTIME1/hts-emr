package com.htsemr.master

interface CounterQueryService {

    fun counter(counterId: Long): CounterView
}
