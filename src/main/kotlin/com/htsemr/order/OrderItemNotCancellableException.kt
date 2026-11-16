package com.htsemr.order

class OrderItemNotCancellableException(val orderItemId: Long, val status: OrderItemStatus)
    : RuntimeException("오더 항목 $orderItemId 은 $status 상태라 오더 취소할 수 없습니다")
