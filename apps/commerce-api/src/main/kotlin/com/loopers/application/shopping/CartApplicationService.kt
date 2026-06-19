package com.loopers.application.shopping

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.shopping.Cart
import com.loopers.domain.shopping.CartItem
import com.loopers.domain.shopping.CartRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class CartApplicationService(
    private val cartRepository: CartRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    fun getItems(userId: Long): List<CartItemInfo> {
        val cart = cartRepository.findByUserId(userId) ?: return emptyList()
        return cartRepository.findItems(cart.id)
            .map { CartItemInfo(productId = it.productId, quantity = it.quantity) }
    }

    fun countItems(userId: Long): Long {
        val cacheKey = countCacheKey(userId)
        redisTemplate.opsForValue().get(cacheKey)?.let { return it.toLong() }

        val count = cartRepository.countItemsByUserId(userId)
        redisTemplate.opsForValue().set(cacheKey, count.toString())
        return count
    }

    fun addItem(userId: Long, productId: Long, quantity: Int, stockQuantity: Int) {
        validateQuantity(quantity)
        val cart = getOrCreateCart(userId)
        val item = cartRepository.findItem(cart.id, productId)
        val nextQuantity = (item?.quantity ?: 0) + quantity
        validateStock(nextQuantity, stockQuantity)

        if (item == null) {
            cartRepository.saveItem(CartItem(cartId = cart.id, productId = productId, quantity = quantity))
        } else {
            item.increaseQuantity(quantity)
            cartRepository.saveItem(item)
        }
        evictCountCache(userId)
    }

    fun changeQuantity(userId: Long, productId: Long, quantity: Int, stockQuantity: Int) {
        validateQuantity(quantity)
        validateStock(quantity, stockQuantity)
        val cart = cartRepository.findByUserId(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쇼핑카트 상품을 찾을 수 없습니다.")
        val item = cartRepository.findItem(cart.id, productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쇼핑카트 상품을 찾을 수 없습니다.")

        item.changeQuantity(quantity)
        cartRepository.saveItem(item)
        evictCountCache(userId)
    }

    fun removeItem(userId: Long, productId: Long) {
        val cart = cartRepository.findByUserId(userId) ?: return
        val item = cartRepository.findItem(cart.id, productId) ?: return
        cartRepository.deleteItem(item)
        evictCountCache(userId)
    }

    fun clear(userId: Long) {
        val cart = cartRepository.findByUserId(userId) ?: return
        cartRepository.deleteItemsByCartId(cart.id)
        evictCountCache(userId)
    }

    private fun getOrCreateCart(userId: Long): Cart =
        cartRepository.findByUserId(userId) ?: cartRepository.save(Cart(userId = userId))

    private fun validateQuantity(quantity: Int) {
        if (quantity < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "쇼핑카트 수량은 1 이상이어야 합니다.")
        }
    }

    private fun validateStock(quantity: Int, stockQuantity: Int) {
        if (quantity > stockQuantity) {
            throw CoreException(ErrorType.BAD_REQUEST, "현재 재고보다 많은 수량은 쇼핑카트에 담을 수 없습니다.")
        }
    }

    private fun evictCountCache(userId: Long) {
        val cacheKey = countCacheKey(userId)
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisTemplate.delete(cacheKey)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    redisTemplate.delete(cacheKey)
                }
            },
        )
    }

    private fun countCacheKey(userId: Long): String =
        "shopping:cart:count:user:$userId"
}
