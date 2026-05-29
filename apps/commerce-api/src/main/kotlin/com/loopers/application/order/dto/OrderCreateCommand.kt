package com.loopers.application.order.dto

data class OrderCreateCommand(
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Long,
    )
}
