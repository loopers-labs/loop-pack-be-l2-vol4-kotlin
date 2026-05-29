package com.loopers.domain.order

import java.time.LocalDateTime

class OrderCommand {
    data class Checkout(
        val userId: Long,
        val items: List<CheckoutItem>,
        val deliveryAddress: String,
        val deliveryRequest: String,
        val phoneNumber: String,
        val reservationExpiresAt: LocalDateTime,
    )

    data class CheckoutItem(
        val productId: Long,
        val productNameSnapshot: String,
        val brandNameSnapshot: String,
        val priceSnapshot: Long,
        val quantity: Int,
    )

    data class Pay(
        val orderId: Long,
    )

    data class Cancel(
        val orderId: Long,
    )

    data class StartShipping(
        val orderId: Long,
    )

    data class Expire(
        val now: LocalDateTime,
    )
}
