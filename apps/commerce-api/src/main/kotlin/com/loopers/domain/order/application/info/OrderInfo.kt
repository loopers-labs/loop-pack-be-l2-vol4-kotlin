package com.loopers.domain.order.application.info

import com.loopers.domain.order.model.OrderModel

data class OrderInfo(
    val id: Long,
    val orderedUserId: Long,
    val issuedCouponId: Long?,
    val status: String,
    val totalPrice: Long,
    val discountPrice: Long,
    val paymentPrice: Long,
    val items: List<OrderItemInfo>,
) {
    companion object {
        fun from(order: OrderModel): OrderInfo = OrderInfo(
            id = order.id,
            orderedUserId = order.orderedUserId,
            issuedCouponId = order.issuedCouponId,
            status = order.status.name,
            totalPrice = order.totalPrice.value,
            discountPrice = order.discountPrice.value,
            paymentPrice = order.paymentPrice.value,
            items = order.items.map { OrderItemInfo.from(it) },
        )
    }
}
