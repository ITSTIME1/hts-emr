package com.htsemr.reception.repository

import com.htsemr.reception.entity.Eligibility
import org.springframework.data.jpa.repository.JpaRepository

internal interface EligibilityRepository : JpaRepository<Eligibility, Long> {

    /** 그 내원 건의 현재 자격: 순번이 가장 큰 기록. 다음 기록의 순번도 이 값에 1을 더해 정한다. */
    fun findFirstByVisitIdOrderBySeqDesc(visitId: Long): Eligibility?
}
