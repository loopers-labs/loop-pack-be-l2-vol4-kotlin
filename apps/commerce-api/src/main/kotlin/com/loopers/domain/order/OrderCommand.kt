package com.loopers.domain.order

import java.time.LocalDateTime

class OrderCommand {
    data class CheckoutRequest(
        val userId: Long,
        val items: List<CheckoutRequestItem>,
        val deliveryAddress: String,
        val deliveryRequest: String,
        val phoneNumber: String,
        val reservationExpiresAt: LocalDateTime,
        val couponId: Long? = null,
    )

    data class CheckoutRequestItem(
        val productId: Long,
        val quantity: Int,
    )

    data class Checkout(
        val userId: Long,
        val items: List<CheckoutItem>,
        val deliveryAddress: String,
        val deliveryRequest: String,
        val phoneNumber: String,
        val reservationExpiresAt: LocalDateTime,
        val couponId: Long? = null,
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
        val paymentKey: String = "",
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
