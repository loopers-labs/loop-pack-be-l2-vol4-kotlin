@file:Suppress("ktlint:standard:filename")

package com.loopers.domain.order

data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
