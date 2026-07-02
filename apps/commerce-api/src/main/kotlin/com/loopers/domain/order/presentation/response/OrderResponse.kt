package com.loopers.domain.order.presentation.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.loopers.domain.order.application.info.OrderInfo

data class OrderResponse(
    val id: Long,
    val orderedUserId: Long,
    @field:JsonProperty("couponId")
    val issuedCouponId: Long?,
    val status: String,
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
            status = info.status,
            totalPrice = info.totalPrice,
            discountPrice = info.discountPrice,
            paymentPrice = info.paymentPrice,
            items = info.items.map { OrderItemResponse.from(it) },
        )
    }
}
