package com.htsemr.reception.service

import com.htsemr.reception.EligibilityQueryService
import com.htsemr.reception.EligibilityView
import com.htsemr.reception.VisitNotFoundException
import com.htsemr.reception.mapping.toEligibilityView
import com.htsemr.reception.repository.EligibilityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
internal class EligibilityQueryServiceImpl(
    private val eligibilityRepository: EligibilityRepository,
) : EligibilityQueryService {

    override fun currentEligibility(visitId: Long): EligibilityView {
        val eligibilityEntity = eligibilityRepository.findFirstByVisitIdOrderBySeqDesc(visitId)
            ?: throw VisitNotFoundException(visitId)
        return eligibilityEntity.toEligibilityView()
    }
}
