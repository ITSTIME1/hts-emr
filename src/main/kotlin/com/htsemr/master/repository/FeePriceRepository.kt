package com.htsemr.master.repository

import com.htsemr.master.entity.FeePrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

internal interface FeePriceRepository : JpaRepository<FeePrice, Long> {

    @Query("""
        select p from FeePrice p
        where p.feeItemId = :feeItemId
          and p.effectiveFrom <= :visitDate
        order by p.effectiveFrom desc
        limit 1
    """)
    fun findEffectiveOn(feeItemId: Long, visitDate: LocalDate): FeePrice?

}
