package com.loopers.application.order.dto

data class OrderCreateCommand(
    val items: List<Item>,
    val couponId: Long? = null,
) {
    data class Item(
        val productId: Long,
        val quantity: Long,
    )
}
