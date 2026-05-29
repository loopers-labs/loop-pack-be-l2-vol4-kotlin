package com.loopers.application.shopping

data class CartItemInfo(
    val productId: Long,
    val quantity: Int,
)

data class CartProductInfo(
    val productId: Long,
    val productName: String,
    val brandName: String,
    val price: Long,
    val stockQuantity: Int,
    val orderable: Boolean,
)

data class CartLineInfo(
    val productId: Long,
    val productName: String?,
    val brandName: String?,
    val price: Long?,
    val quantity: Int,
    val stockQuantity: Int?,
    val orderable: Boolean,
)

data class CartInfo(
    val userId: Long,
    val items: List<CartLineInfo>,
)
