package com.loopers.interfaces.api.order

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.order.OrderInfo
import com.loopers.application.order.OrderItemInfo
import com.loopers.domain.order.OrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

class OrderV1Dto {
    data class PlaceOrderRequest(
        @field:Valid
        @field:NotEmpty
        val items: List<OrderItemRequest>,
        val couponId: Long? = null,
    ) {
        fun toCommand(userId: Long): CreateOrderCommand =
            CreateOrderCommand(
                userId = userId,
                items = items.map { it.toCommand() },
                userCouponId = couponId,
            )
    }

    data class OrderItemRequest(
        @field:Positive
        val productId: Long,
        @field:Positive
        val quantity: Int,
    ) {
        fun toCommand(): CreateOrderItemCommand =
            CreateOrderItemCommand(productId = productId, quantity = quantity)
    }

    data class PlaceOrderResponse(
        val id: Long,
        val userId: Long,
        val status: OrderStatus,
        val totalAmount: Long,
        val discountAmount: Long,
        val paymentAmount: Long,
        val items: List<OrderItemResponse>,
    ) {
        companion object {
            fun from(info: OrderInfo): PlaceOrderResponse =
                PlaceOrderResponse(
                    id = info.id,
                    userId = info.userId,
                    status = info.status,
                    totalAmount = info.totalAmount,
                    discountAmount = info.discountAmount,
                    paymentAmount = info.paymentAmount,
                    items = info.items.map { OrderItemResponse.from(it) },
                )
        }
    }

    data class OrderItemResponse(
        val productId: Long,
        val productName: String,
        val productPrice: Long,
        val quantity: Int,
        val totalPrice: Long,
    ) {
        companion object {
            fun from(info: OrderItemInfo): OrderItemResponse =
                OrderItemResponse(
                    productId = info.productId,
                    productName = info.productName,
                    productPrice = info.productPrice,
                    quantity = info.quantity,
                    totalPrice = info.totalPrice,
                )
        }
    }
}
