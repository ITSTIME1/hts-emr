package com.htsemr.master.repository

import com.htsemr.master.entity.FeePrice
import org.springframework.data.jpa.repository.JpaRepository

internal interface FeePriceRepository : JpaRepository<FeePrice, Long> {}