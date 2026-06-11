package com.loopers.domain.order.presentation.response

import com.loopers.domain.order.application.info.OrderInfo
import com.loopers.domain.order.application.info.OrderItemInfo

data class OrderResponse(
    val id: Long,
    val orderedUserId: Long,
    val issuedCouponId: Long?,
    val totalPrice: Long,
    val discountPrice: Long,
    val paymentPrice: Long,
    val items: List<OrderItemResponse>,
) {
    companion object {
        fun from(info: OrderInfo): OrderResponse = OrderResponse(
            id = info.id,
            orderedUserId = info.orderedUserId,
            issuedCouponId = info.issuedCouponId,
            totalPrice = info.totalPrice,
            discountPrice = info.discountPrice,
            paymentPrice = info.paymentPrice,
            items = info.items.map { OrderItemResponse.from(it) },
        )
    }
}

data class OrderItemResponse(
    val productId: Long,
    val quantity: Long,
    val productName: String,
    val unitPrice: Long,
    val linePrice: Long,
) {
    companion object {
        fun from(info: OrderItemInfo): OrderItemResponse = OrderItemResponse(
            productId = info.productId,
            quantity = info.quantity,
            productName = info.productName,
            unitPrice = info.unitPrice,
            linePrice = info.linePrice,
        )
    }
}
