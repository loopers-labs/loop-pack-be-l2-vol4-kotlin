package com.loopers.domain.order

data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val totalPrice: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val items: List<OrderLine>,
) {
    data class OrderLine(
        val productId: Long,
        val quantity: Int,
        val lineTotal: Long,
    )

    companion object {
        fun from(order: OrderModel): OrderCreatedEvent = OrderCreatedEvent(
            orderId = order.id,
            userId = order.userId,
            totalPrice = order.totalPrice,
            discountAmount = order.discountAmount,
            finalAmount = order.finalAmount,
            items = order.items.map { OrderLine(productId = it.productId, quantity = it.quantity, lineTotal = it.lineTotal) },
        )
    }
}
