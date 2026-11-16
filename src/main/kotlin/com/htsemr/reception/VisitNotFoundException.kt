package com.htsemr.reception

class VisitNotFoundException(val visitId: Long)
    : RuntimeException("내원 건 $visitId 이 없습니다")
