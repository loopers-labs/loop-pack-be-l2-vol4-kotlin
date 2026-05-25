package com.loopers.infrastructure.stock

import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<StockEntity, Long> {
    fun findByProductId(productId: Long): StockEntity?
}
