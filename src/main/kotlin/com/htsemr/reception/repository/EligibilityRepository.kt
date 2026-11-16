package com.htsemr.reception.repository

import com.htsemr.reception.entity.Eligibility
import org.springframework.data.jpa.repository.JpaRepository

internal interface EligibilityRepository : JpaRepository<Eligibility, Long> {

    fun findFirstByVisitIdOrderBySeqDesc(visitId: Long): Eligibility?
}
