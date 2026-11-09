package com.htsemr.reception

/**
 * 내원 건의 자격 조회. reception 모듈의 공개 기능이며 조회만 한다.
 *
 * - billing: 본인부담률로 본인부담금을 계산한다.
 * - payment: 자격 소급 변경 뒤 재산정할 때 새 자격을 확인한다(F-04.d).
 */
interface EligibilityQueryService {

    /**
     * 그 내원 건의 현재 자격: 순번이 가장 큰 기록.
     * 기존 기록은 고치지 않으므로, 이전에 받은 결과는 그때의 기록으로 남아 있다(A-03).
     *
     * @throws VisitNotFoundException 내원 건이 없을 때.
     *   접수는 내원 건과 첫 자격 기록을 함께 저장하므로, 자격 기록이 없으면 내원 건이 없는 것이다.
     */
    fun currentEligibility(visitId: Long): EligibilityView
}
