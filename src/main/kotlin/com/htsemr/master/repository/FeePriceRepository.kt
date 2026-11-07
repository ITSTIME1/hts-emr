package com.htsemr.master.repository

import com.htsemr.master.entity.FeePrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

internal interface FeePriceRepository : JpaRepository<FeePrice, Long> {

    /** 그 진료일에 유효한 단가: 적용 시작일이 진료일 이전인 것 가운데 가장 최근 */
    @Query("""
        select p from FeePrice p
        where p.feeItemId = :feeItemId
          and p.effectiveFrom <= :visitDate
        order by p.effectiveFrom desc
        limit 1
    """)
    fun findEffectiveOn(feeItemId: Long, visitDate: LocalDate): FeePrice?

}