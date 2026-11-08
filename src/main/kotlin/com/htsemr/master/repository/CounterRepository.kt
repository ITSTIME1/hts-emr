package com.htsemr.master.repository
import com.htsemr.master.entity.Counter
import org.springframework.data.jpa.repository.JpaRepository

internal interface CounterRepository : JpaRepository<Counter, Long> {}