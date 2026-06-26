package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

class OrderV1Dto {
    data class CheckoutRequest(
        @field:NotEmpty
        @field:Valid
        val items: List<CheckoutItemRequest>,

        @field:NotBlank
        val deliveryAddress: String,

        val deliveryRequest: String = "",

        @field:NotBlank
        val phoneNumber: String,

        @field:NotNull
        val reservationExpiresAt: LocalDateTime,

        val couponId: Long? = null,
    ) {
        fun toCommand(userId: Long): OrderCommand.CheckoutRequest = OrderCommand.CheckoutRequest(
            userId = userId,
            items = items.map(CheckoutItemRequest::toCommand),
            deliveryAddress = deliveryAddress,
            deliveryRequest = deliveryRequest,
            phoneNumber = phoneNumber,
            reservationExpiresAt = reservationExpiresAt,
            couponId = couponId,
        )
    }

    data class CheckoutItemRequest(
        @field:Positive
        val productId: Long,

        @field:Positive
        val quantity: Int,
    ) {
        fun toCommand(): OrderCommand.CheckoutRequestItem = OrderCommand.CheckoutRequestItem(
            productId = productId,
            quantity = quantity,
        )
    }

    data class PayRequest(
        /** Card issuer/type value passed through to the PG simulator payment request. */
        @field:NotBlank
        val cardType: String,

        /** Card number value passed through to the PG simulator payment request. */
        @field:NotBlank
        val cardNo: String,
    ) {
        /** Builds the domain command that starts a PG simulator transaction for this order. */
        fun toCommand(userId: Long, orderId: Long): OrderCommand.Pay = OrderCommand.Pay(
            userId = userId,
            orderId = orderId,
            cardType = cardType,
            cardNo = cardNo,
        )
    }

    data class OrderResponse(
        val orderId: Long,
        val status: OrderStatus,
        val reservationExpiresAt: LocalDateTime,
        val cancelReason: OrderCancelReason?,
        val deliveryAddress: String,
        val deliveryRequest: String,
        val phoneNumber: String,
        val couponId: Long?,
        val totalAmount: Long,
        val discountAmount: Long,
        val paymentAmount: Long,
        val items: List<OrderItemResponse>,
    ) {
        companion object {
            fun from(info: OrderInfo.Detail) = OrderResponse(
                orderId = info.orderId,
                status = info.status,
                reservationExpiresAt = info.reservationExpiresAt,
                cancelReason = info.cancelReason,
                deliveryAddress = info.deliveryAddress,
                deliveryRequest = info.deliveryRequest,
                phoneNumber = info.phoneNumber,
                couponId = info.couponId,
                totalAmount = info.totalAmount,
                discountAmount = info.discountAmount,
                paymentAmount = info.paymentAmount,
                items = info.items.map(OrderItemResponse::from),
            )
        }
    }

    data class OrderItemResponse(
        val productId: Long,
        val productNameSnapshot: String,
        val brandNameSnapshot: String,
        val priceSnapshot: Long,
        val quantity: Int,
    ) {
        companion object {
            fun from(info: OrderInfo.Item) = OrderItemResponse(
                productId = info.productId,
                productNameSnapshot = info.productNameSnapshot,
                brandNameSnapshot = info.brandNameSnapshot,
                priceSnapshot = info.priceSnapshot,
                quantity = info.quantity,
            )
        }
    }
}
