package com.htsemr.common

data class Money(val amount : Long) : Comparable<Money>  {
    companion object {
        val ZERO = Money(0)
    }

    operator fun plus(other : Money) = Money(amount + other.amount)
    operator fun minus(other : Money) = Money(amount - other.amount)

    operator fun times(multiplier : Int) = Money(amount * multiplier)
    override fun compareTo(other: Money) = amount.compareTo(other.amount)
}
