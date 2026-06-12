package com.loopers.application.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import java.math.BigDecimal

data class OrderInfo(
    val id: Long,
    val userId: Long,
    val status: OrderStatus,
    val totalPrice: BigDecimal,
    val discountAmount: BigDecimal,
    val paidPrice: BigDecimal,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val productName: String,
        val price: BigDecimal,
        val quantity: Int,
        val subtotal: BigDecimal,
    )

    companion object {
        fun from(order: OrderModel): OrderInfo {
            return OrderInfo(
                id = order.id,
                userId = order.userId,
                status = order.status,
                totalPrice = order.totalPrice,
                discountAmount = order.discountAmount,
                paidPrice = order.paidPrice,
                items = order.items.map {
                    Item(
                        productId = it.productId,
                        productName = it.productName,
                        price = it.price,
                        quantity = it.quantity,
                        subtotal = it.subtotal(),
                    )
                },
            )
        }
    }
}
