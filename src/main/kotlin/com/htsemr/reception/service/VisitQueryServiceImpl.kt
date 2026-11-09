package com.htsemr.reception.service

import com.htsemr.reception.VisitNotFoundException
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import com.htsemr.reception.mapping.toVisitView
import com.htsemr.reception.repository.VisitRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
internal class VisitQueryServiceImpl(
    private val visitRepository: VisitRepository,
) : VisitQueryService {

    override fun visit(visitId: Long): VisitView {
        val visitEntity = visitRepository.findByIdOrNull(visitId)
            ?: throw VisitNotFoundException(visitId)
        return visitEntity.toVisitView()
    }
}
