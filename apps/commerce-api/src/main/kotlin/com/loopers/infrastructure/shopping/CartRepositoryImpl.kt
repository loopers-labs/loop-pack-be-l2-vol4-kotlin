package com.loopers.infrastructure.shopping

import com.loopers.domain.shopping.Cart
import com.loopers.domain.shopping.CartItem
import com.loopers.domain.shopping.CartRepository
import org.springframework.stereotype.Component

@Component
class CartRepositoryImpl(
    private val cartJpaRepository: CartJpaRepository,
    private val cartItemJpaRepository: CartItemJpaRepository,
) : CartRepository {
    override fun findByUserId(userId: Long): Cart? =
        cartJpaRepository.findByUserId(userId)

    override fun save(cart: Cart): Cart =
        cartJpaRepository.save(cart)

    override fun findItem(cartId: Long, productId: Long): CartItem? =
        cartItemJpaRepository.findByCartIdAndProductId(cartId, productId)

    override fun findItems(cartId: Long): List<CartItem> =
        cartItemJpaRepository.findAllByCartIdOrderByIdAsc(cartId)

    override fun countItemsByUserId(userId: Long): Long =
        cartItemJpaRepository.countByUserId(userId)

    override fun saveItem(item: CartItem): CartItem =
        cartItemJpaRepository.save(item)

    override fun deleteItem(item: CartItem) {
        cartItemJpaRepository.delete(item)
    }

    override fun deleteItemsByCartId(cartId: Long) {
        cartItemJpaRepository.deleteAll(cartItemJpaRepository.findAllByCartIdOrderByIdAsc(cartId))
    }
}
