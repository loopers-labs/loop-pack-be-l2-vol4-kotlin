package com.loopers.domain.order

class OrderItem(
    val id: Long? = null,
    val productSnapshot: ProductSnapshot,
    val quantity: OrderQuantity,
) {
    val productId: Long
        get() = productSnapshot.productId

    val productName: String
        get() = productSnapshot.productName

    val productPrice: OrderItemPrice
        get() = productSnapshot.productPrice

    val totalPrice: OrderAmount
        get() = productPrice * quantity
}
