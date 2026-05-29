package com.loopers.application.shopping

import com.loopers.domain.BaseEntity
import com.loopers.domain.shopping.Cart
import com.loopers.domain.shopping.CartItem
import com.loopers.domain.shopping.CartRepository
import java.util.concurrent.atomic.AtomicLong

class FakeCartRepository : CartRepository {
    private val carts = mutableMapOf<Long, Cart>()
    private val items = mutableMapOf<Long, CartItem>()
    private val cartSequence = AtomicLong(1)
    private val itemSequence = AtomicLong(1)

    override fun findByUserId(userId: Long): Cart? =
        carts.values.firstOrNull { it.userId == userId }

    override fun save(cart: Cart): Cart {
        assignIdIfNeeded(cart, cartSequence)
        carts[cart.id] = cart
        return cart
    }

    override fun findItem(cartId: Long, productId: Long): CartItem? =
        items.values.firstOrNull { it.cartId == cartId && it.productId == productId }

    override fun findItems(cartId: Long): List<CartItem> =
        items.values.filter { it.cartId == cartId }.sortedBy { it.id }

    override fun saveItem(item: CartItem): CartItem {
        assignIdIfNeeded(item, itemSequence)
        items[item.id] = item
        return item
    }

    override fun deleteItem(item: CartItem) {
        items.remove(item.id)
    }

    override fun deleteItemsByCartId(cartId: Long) {
        items.entries.removeIf { it.value.cartId == cartId }
    }

    private fun assignIdIfNeeded(entity: BaseEntity, sequence: AtomicLong) {
        if (entity.id == 0L) {
            idField.setLong(entity, sequence.getAndIncrement())
        }
    }

    companion object {
        private val idField = BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }
    }
}
