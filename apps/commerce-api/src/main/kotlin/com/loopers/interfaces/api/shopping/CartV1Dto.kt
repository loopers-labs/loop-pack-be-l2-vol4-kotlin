package com.loopers.interfaces.api.shopping

import com.loopers.application.shopping.CartCommand
import com.loopers.application.shopping.CartInfo
import com.loopers.application.shopping.CartLineInfo
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

class CartV1Dto {
    data class AddItemRequest(
        @field:NotNull
        val productId: Long,

        @field:Min(1)
        val quantity: Int,
    ) {
        fun toCommand(userId: Long): CartCommand.AddItem =
            CartCommand.AddItem(userId = userId, productId = productId, quantity = quantity)
    }

    data class ChangeQuantityRequest(
        @field:Min(1)
        val quantity: Int,
    ) {
        fun toCommand(userId: Long, productId: Long): CartCommand.ChangeQuantity =
            CartCommand.ChangeQuantity(userId = userId, productId = productId, quantity = quantity)
    }

    data class CheckoutRequest(
        @field:NotBlank
        val deliveryAddress: String,

        val deliveryRequest: String = "",

        @field:NotBlank
        val phoneNumber: String,

        @field:NotNull
        val reservationExpiresAt: LocalDateTime,
    ) {
        fun toCommand(userId: Long): CartCommand.Checkout =
            CartCommand.Checkout(
                userId = userId,
                deliveryAddress = deliveryAddress,
                deliveryRequest = deliveryRequest,
                phoneNumber = phoneNumber,
                reservationExpiresAt = reservationExpiresAt,
            )
    }

    data class CartResponse(
        val userId: Long,
        val items: List<CartLineResponse>,
    ) {
        companion object {
            fun from(info: CartInfo): CartResponse =
                CartResponse(userId = info.userId, items = info.items.map(CartLineResponse::from))
        }
    }

    data class CartLineResponse(
        val productId: Long,
        val productName: String?,
        val brandName: String?,
        val price: Long?,
        val quantity: Int,
        val stockQuantity: Int?,
        val orderable: Boolean,
    ) {
        companion object {
            fun from(info: CartLineInfo): CartLineResponse =
                CartLineResponse(
                    productId = info.productId,
                    productName = info.productName,
                    brandName = info.brandName,
                    price = info.price,
                    quantity = info.quantity,
                    stockQuantity = info.stockQuantity,
                    orderable = info.orderable,
                )
        }
    }
}
