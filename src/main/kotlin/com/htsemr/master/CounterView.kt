package com.htsemr.master

/** 창구. master 모듈 밖으로 내보내는 값 객체다. */
data class CounterView(
    val id: Long,
    val code: String,
    val name: String,
)
