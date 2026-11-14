package com.htsemr.common

data class Money(val amount : Long) : Comparable<Money>  {
    companion object {
        val ZERO = Money(0)
    }

    /**
     * 연산자 오버로딩(Operator Overloading)을 적용합니다.
     *
     * [Sample Code]
     * val moneyA = Money(100)
     * val moneyB = Money(200)
     *
     * val sumAB = moneyA + moneyB   // 내부적으로 moneyA.plus(moneyB)를 호출하여 amount를 더한 새로운 Money 객체를 반환합니다.
     * val diffAB = moneyB - moneyA  // 내부적으로 moneyB.minus(moneyA)를 호출하여 amount를 뺀 새로운 Money 객체를 반환합니다.
     *
     * val isMore = moneyB > moneyA  // 내부적으로 compareTo() 호출 -> true 반환
     *
     *
     * 반환값 의미:
     * 양수 (> 0): 기준 객체(this)가 비교 대상(other)보다 큼
     * 0: 두 객체의 금액이 같음
     * 음수 (< 0): 기준 객체(this)가 비교 대상(other)보다 작음
     */
    operator fun plus(other : Money) = Money(amount + other.amount)
    operator fun minus(other : Money) = Money(amount - other.amount)

    /**
     * 정수배. 예: 단가 × 수량.
     * 비율을 곱하거나 끝자리를 버리는 계산은 업무 규칙이라 여기 두지 않는다(billing의 끝전 처리, common.md).
     */
    operator fun times(multiplier : Int) = Money(amount * multiplier)
    override fun compareTo(other: Money) = amount.compareTo(other.amount)
}