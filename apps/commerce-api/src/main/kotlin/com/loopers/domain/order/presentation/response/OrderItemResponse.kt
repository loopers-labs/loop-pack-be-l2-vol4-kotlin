package com.loopers.domain.order.presentation.response

import com.loopers.domain.order.application.info.OrderItemInfo

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
