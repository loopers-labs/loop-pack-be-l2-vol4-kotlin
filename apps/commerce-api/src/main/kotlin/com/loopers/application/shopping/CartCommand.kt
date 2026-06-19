package com.loopers.application.shopping

import java.time.LocalDateTime

class CartCommand {
    data class AddItem(
        val userId: Long,
        val productId: Long,
        val quantity: Int,
    )

    data class ChangeQuantity(
        val userId: Long,
        val productId: Long,
        val quantity: Int,
    )

    data class RemoveItem(
        val userId: Long,
        val productId: Long,
    )

    data class Clear(
        val userId: Long,
    )

    data class Checkout(
        val userId: Long,
        val deliveryAddress: String,
        val deliveryRequest: String,
        val phoneNumber: String,
        val reservationExpiresAt: LocalDateTime,
    )
}
