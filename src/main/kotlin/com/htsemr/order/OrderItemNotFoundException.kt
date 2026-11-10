package com.htsemr.order

/** 오더 항목이 없다. */
class OrderItemNotFoundException(val orderItemId: Long)
    : RuntimeException("오더 항목 $orderItemId 이 없습니다")
