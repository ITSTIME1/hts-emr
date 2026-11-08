package com.htsemr.reception.service

import com.htsemr.reception.DuplicateVisitException
import com.htsemr.reception.RegistrationService
import com.htsemr.reception.entity.Eligibility
import com.htsemr.reception.entity.Visit
import com.htsemr.reception.external.EligibilityProvider
import com.htsemr.reception.repository.EligibilityRepository
import com.htsemr.reception.repository.VisitRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 접수 구현.
 *
 * [외부 조회는 트랜잭션 밖에서]
 * 이 메서드에는 @Transactional을 붙이지 않는다. 외부 기관 응답을 기다리는 동안 DB 연결을 붙잡지 않기 위해서다.
 * 외부 기관이 느려져도 연결 풀이 바닥나 다른 업무(수납 등)까지 멈추지 않게 한다.
 * 조회를 먼저 끝내고, 내원 건과 첫 자격 기록 저장만 한 트랜잭션으로 묶는다.
 */
@Service
internal class RegistrationServiceImpl(
    private val eligibilityProvider: EligibilityProvider,
    private val visitRepository: VisitRepository,
    private val eligibilityRepository: EligibilityRepository,
    private val transactionTemplate: TransactionTemplate,
) : RegistrationService {

    override fun registerVisit(patientNo: String, departmentCode: String, doctorNo: String, visitDate: LocalDate): Long {
        // ① 외부 기관 조회: 트랜잭션 밖
        val response = eligibilityProvider.inquireEligibility(patientNo, visitDate)
        val now = LocalDateTime.now()

        // ② 내원 건과 첫 자격 기록 저장: 한 트랜잭션. 둘 중 하나라도 실패하면 둘 다 남지 않는다.
        return try {
            transactionTemplate.execute {
                val visit = visitRepository.saveAndFlush(
                    Visit(
                        patientNo = patientNo,
                        departmentCode = departmentCode,
                        doctorNo = doctorNo,
                        visitDate = visitDate,
                        registeredAt = now,
                    )
                )
                val visitId = requireNotNull(visit.id)

                eligibilityRepository.save(
                    Eligibility(
                        visitId = visitId,
                        seq = FIRST_SEQ,
                        insuranceType = response.insuranceType,
                        copayRate = response.copayRate,
                        recordedAt = now,
                    )
                )
                visitId
            }!!
        } catch (e: DataIntegrityViolationException) {
            // 중복 접수는 미리 조회해서 막지 않고 DB 유일 제약에 맡긴다.
            // 미리 조회하면 동시에 들어온 두 접수가 둘 다 "없음"을 보고 통과할 수 있기 때문이다.
            if (e.isViolationOf(UK_VISIT_PATIENT_DATE_DEPT)) {
                throw DuplicateVisitException(patientNo, visitDate, departmentCode, e)
            }
            throw e
        }
    }

    private fun DataIntegrityViolationException.isViolationOf(constraint: String) =
        generateSequence<Throwable>(this) { it.cause }
            .any { it.message?.contains(constraint, ignoreCase = true) == true }

    private companion object {
        /** 접수 때 남기는 자격 기록은 그 내원 건의 첫 기록이다. */
        const val FIRST_SEQ = 1
        const val UK_VISIT_PATIENT_DATE_DEPT = "uk_visit_patient_date_dept"
    }
}
