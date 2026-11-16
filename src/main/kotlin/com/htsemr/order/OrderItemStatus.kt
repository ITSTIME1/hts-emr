package com.htsemr.order
enum class OrderItemStatus(val isEffective : Boolean) {
    ORDERED(true),
    EXECUTED(true),
    CANCEL_REQUESTED(false),
    CANCELLED(false),
    EXPIRED(false),
}
