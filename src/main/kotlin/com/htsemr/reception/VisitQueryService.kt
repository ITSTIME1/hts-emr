package com.htsemr.reception

/**
 * 내원 건 조회. reception 모듈의 공개 기능이며 조회만 한다.
 *
 * - order: 오더를 낼 내원 건이 있는지 확인하고(T-ORD-01), 진료일 경과를 판단한다.
 * - billing: 진료일로 그날의 수가를 찾는다(A-06).
 */
interface VisitQueryService {

    /**
     * 내원 건 하나를 조회한다.
     *
     * @throws VisitNotFoundException 내원 건이 없을 때
     */
    fun visit(visitId: Long): VisitView
}
