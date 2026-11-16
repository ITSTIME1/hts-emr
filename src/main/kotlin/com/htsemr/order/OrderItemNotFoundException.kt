package com.htsemr.order

class OrderItemNotFoundException(val orderItemId: Long)
    : RuntimeException("오더 항목 $orderItemId 이 없습니다")
