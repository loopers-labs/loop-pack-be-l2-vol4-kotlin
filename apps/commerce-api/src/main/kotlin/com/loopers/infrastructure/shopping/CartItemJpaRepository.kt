package com.loopers.infrastructure.shopping

import com.loopers.domain.shopping.CartItem
import org.springframework.data.jpa.repository.JpaRepository

interface CartItemJpaRepository : JpaRepository<CartItem, Long> {
    fun findByCartIdAndProductId(cartId: Long, productId: Long): CartItem?

    fun findAllByCartIdOrderByIdAsc(cartId: Long): List<CartItem>
}
