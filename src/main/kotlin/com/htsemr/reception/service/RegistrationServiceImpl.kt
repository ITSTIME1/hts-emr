package com.htsemr.reception.service

import com.htsemr.reception.DuplicateVisitException
import com.htsemr.reception.RegistrationService
import com.htsemr.reception.entity.Eligibility
import com.htsemr.reception.entity.Visit
import com.htsemr.reception.external.EligibilityProvider
import com.htsemr.reception.repository.EligibilityRepository
import com.htsemr.reception.repository.VisitRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

@Service
internal class RegistrationServiceImpl(
    private val eligibilityProvider: EligibilityProvider,
    private val visitRepository: VisitRepository,
    private val eligibilityRepository: EligibilityRepository,
    private val transactionTemplate: TransactionTemplate,
) : RegistrationService {

    override fun registerVisit(patientNo: String, departmentCode: String, doctorNo: String, visitDate: LocalDate): Long {
        val processStartedAt = LocalDateTime.now()

        val eligibilityResponse = eligibilityProvider.inquireEligibility(patientNo = patientNo, visitDate = visitDate)

        return try {
            transactionTemplate.execute {

                val savedVisit = visitRepository.saveAndFlush(
                    Visit(
                        patientNo = patientNo,
                        departmentCode = departmentCode,
                        doctorNo = doctorNo,
                        visitDate = visitDate,
                        registeredAt = processStartedAt,
                    )
                )

                eligibilityRepository.save(
                    Eligibility(
                        visitId = requireNotNull(savedVisit.id),
                        seq = 1,
                        insuranceType = eligibilityResponse.insuranceType,
                        copayRate = eligibilityResponse.copayRate,
                        recordedAt = processStartedAt,
                    )
                )
                savedVisit.id
            } ?: error("내원 건 저장 트랜잭션이 결과를 돌려주지 않았습니다")
        } catch(e: DataIntegrityViolationException) {
            val violatedConstraint = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .firstOrNull()
                ?.constraintName
                ?.substringAfterLast('.')

            if (violatedConstraint.equals("uk_visit_patient_date_dept", ignoreCase = true)) {
                throw DuplicateVisitException(patientNo, visitDate, departmentCode, e)
            }

            throw e
        }
    }

}
