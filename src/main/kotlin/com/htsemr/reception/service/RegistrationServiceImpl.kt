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
        val processStartedAt = LocalDateTime.now()

        // 1. 외부 공단에 자격을 조회한다.
        //    외부 호출은 트랜잭션 밖에서 한다.
        val eligibilityResponse = eligibilityProvider.inquireEligibility(patientNo = patientNo, visitDate = visitDate)

        // 2. 내원 건과 자격 기록을 한 트랜잭션 안에서 저장한다.
        //    둘 중 하나라도 실패하면 둘 다 남지 않는다.
        return try {
            transactionTemplate.execute {

                // 3. 내원 건을 저장한다.
                //    같은 환자·진료일·진료과가 이미 있으면 유일 제약(uk_visit_patient_date_dept)이 거부한다.
                val savedVisit = visitRepository.saveAndFlush(
                    Visit(
                        patientNo = patientNo,
                        departmentCode = departmentCode,
                        doctorNo = doctorNo,
                        visitDate = visitDate,
                        registeredAt = processStartedAt,
                    )
                )

                // 4. 자격 기록을 저장한다.
                //    같은 내원 건의 자격 기록은 seq로 구분하고, (내원 건, seq)는 유일 제약(uk_eligibility_visit_seq)이 하나만 허용한다.
                //    seq가 항상 1인 이유: 3에서 saveAndFlush가 성공했다면 그 내원 건은 방금 만든 새 IDENTITY를 가진다.
                //    따라서 이 기록은 그 내원 건의 첫 자격 기록이다.
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
            // 5. 위반한 제약의 이름을 꺼낸다.
            //    제약 위반은 어떤 제약이든, 길이 초과 같은 다른 오류든 같은 DataIntegrityViolationException으로 온다.
            //    그래서 예외 타입이 아니라 제약 이름으로 원인을 구분한다.
            val violatedConstraint = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .firstOrNull()
                ?.constraintName
                ?.substringAfterLast('.')

            // 6. 같은 내원 건(uk_visit_patient_date_dept)일 때만 업무 예외로 바꾼다.
            if (violatedConstraint.equals("uk_visit_patient_date_dept", ignoreCase = true)) {
                throw DuplicateVisitException(patientNo, visitDate, departmentCode, e)
            }

            // 7. 나머지는 그대로 던진다.
            //    자격 기록 순번 중복(uk_eligibility_visit_seq)은 접수에서 정상적으로는 생길 수 없으므로 로직의 문제다.
            //    업무 예외로 바꾸지 않고 시스템 예외로 드러낸다.
            throw e
        }
    }

}
