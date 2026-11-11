package com.htsemr.order
/**
 * 오더 항목의 상태. 다른 모듈이 효력을 판단할 때 쓰므로 공개한다.
 * 효력이 있는 상태는 ORDERED, EXECUTED뿐이다(INV-04).
 */
enum class OrderItemStatus(val isEffective : Boolean) {
    ORDERED(true),
    EXECUTED(true),
    CANCEL_REQUESTED(false),
    CANCELLED(false),
    EXPIRED(false),
}