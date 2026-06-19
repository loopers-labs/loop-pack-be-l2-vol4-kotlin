package com.loopers.infrastructure.shopping

import com.loopers.domain.shopping.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CartItemJpaRepository : JpaRepository<CartItem, Long> {
    fun findByCartIdAndProductId(cartId: Long, productId: Long): CartItem?

    fun findAllByCartIdOrderByIdAsc(cartId: Long): List<CartItem>

    @Query(
        value = """
            select count(ci.id)
            from cart_items ci
            join carts c on c.id = ci.cart_id
            where c.user_id = :userId
        """,
        nativeQuery = true,
    )
    fun countByUserId(@Param("userId") userId: Long): Long
}
