package com.loopers.domain.order

enum class OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    ;

    fun isIncomplete(): Boolean = this in INCOMPLETE_STATUSES

    fun canTransitionTo(next: OrderStatus): Boolean = when (this) {
        CREATED -> next == PAYMENT_PENDING
        PAYMENT_PENDING -> next == PAYMENT_COMPLETED || next == CANCELLED
        PAYMENT_COMPLETED -> next == SHIPPING
        SHIPPING -> next == DELIVERED
        DELIVERED, CANCELLED -> false
    }

    companion object {
        val INCOMPLETE_STATUSES: Set<OrderStatus> =
            setOf(CREATED, PAYMENT_PENDING, PAYMENT_COMPLETED, SHIPPING)
    }
}
