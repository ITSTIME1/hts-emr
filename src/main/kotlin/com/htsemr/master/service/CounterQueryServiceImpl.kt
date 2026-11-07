package com.htsemr.master.service

import com.htsemr.master.CounterNotFoundException
import com.htsemr.master.CounterQueryService
import com.htsemr.master.CounterView
import com.htsemr.master.mapping.toCounterView
import com.htsemr.master.repository.CounterRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
internal class CounterQueryServiceImpl(
    private val counterRepository: CounterRepository,
) : CounterQueryService {

    override fun counter(counterId: Long): CounterView {
        val counterEntity = counterRepository.findByIdOrNull(counterId)
            ?: throw CounterNotFoundException(counterId)
        return counterEntity.toCounterView()
    }
}
