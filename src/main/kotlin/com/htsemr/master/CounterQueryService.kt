package com.htsemr.master

/**
 * 창구 조회. master 모듈의 공개 기능이며 조회만 한다.
 *
 * - payment: 수납 건이 어느 창구에서 처리되었는지 확인한다.
 * - reconciliation: 창구별로 일 마감을 한다(F-07).
 */
interface CounterQueryService {

    /**
     * 창구 하나를 조회한다.
     *
     * @throws CounterNotFoundException 창구가 없을 때
     */
    fun counter(counterId: Long): CounterView
}
