package com.loopers.domain.shopping

interface CartRepository {
    fun findByUserId(userId: Long): Cart?

    fun save(cart: Cart): Cart

    fun findItem(cartId: Long, productId: Long): CartItem?

    fun findItems(cartId: Long): List<CartItem>

    fun countItemsByUserId(userId: Long): Long

    fun saveItem(item: CartItem): CartItem

    fun deleteItem(item: CartItem)

    fun deleteItemsByCartId(cartId: Long)
}
