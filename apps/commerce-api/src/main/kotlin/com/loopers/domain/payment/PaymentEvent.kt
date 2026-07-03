@file:Suppress("ktlint:standard:filename")

package com.loopers.domain.payment

data class PaymentSucceededEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
