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
        // 내원 건을 따로 조회하지 않는다. 접수가 내원 건과 첫 자격 기록을 함께 저장하므로,
        // 자격 기록이 없으면 내원 건도 없다.
        val eligibilityEntity = eligibilityRepository.findFirstByVisitIdOrderBySeqDesc(visitId)
            ?: throw VisitNotFoundException(visitId)
        return eligibilityEntity.toEligibilityView()
    }
}
