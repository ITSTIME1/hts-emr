package com.htsemr.reception

interface VisitQueryService {

    fun visit(visitId: Long): VisitView
}
