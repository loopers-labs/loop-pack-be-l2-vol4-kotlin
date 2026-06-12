package com.loopers.order.domain

enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    FAILED,
    UNKNOWN,
    ;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        PENDING_PAYMENT -> target == PAID || target == FAILED || target == UNKNOWN
        UNKNOWN -> target == PAID || target == FAILED
        PAID, FAILED -> false
    }
}
