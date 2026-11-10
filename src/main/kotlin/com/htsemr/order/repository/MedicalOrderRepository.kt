package com.htsemr.order.repository

import com.htsemr.order.entity.MedicalOrder
import org.springframework.data.jpa.repository.JpaRepository

internal interface MedicalOrderRepository : JpaRepository<MedicalOrder, Long>
