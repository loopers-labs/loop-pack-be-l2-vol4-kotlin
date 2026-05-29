package com.loopers.infrastructure.shopping

import com.loopers.domain.shopping.Cart
import org.springframework.data.jpa.repository.JpaRepository

interface CartJpaRepository : JpaRepository<Cart, Long> {
    fun findByUserId(userId: Long): Cart?
}
