package com.htsemr.master.repository

import com.htsemr.master.entity.FeeItem
import org.springframework.data.jpa.repository.JpaRepository

internal interface FeeItemRepository : JpaRepository<FeeItem, Long> {}
