package com.htsemr.reception

/** 내원 건이 없다. */
class VisitNotFoundException(val visitId: Long)
    : RuntimeException("내원 건 $visitId 이 없습니다")
