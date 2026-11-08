package com.htsemr.reception.repository

import com.htsemr.reception.entity.Visit
import org.springframework.data.jpa.repository.JpaRepository

internal interface VisitRepository : JpaRepository<Visit, Long>
