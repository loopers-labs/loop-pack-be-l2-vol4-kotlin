package com.loopers.application.order

data class OrderCommand(
    val loginId: String,
    val password: String,
    val items: List<OrderItemCommand>,
    val couponId: Long? = null,
) {
    data class OrderItemCommand(
        val productId: Long,
        val quantity: Int,
    )
}
