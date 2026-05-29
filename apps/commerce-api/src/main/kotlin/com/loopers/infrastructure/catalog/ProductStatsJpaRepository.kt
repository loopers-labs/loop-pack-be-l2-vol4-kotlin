package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStats
import org.springframework.data.jpa.repository.JpaRepository

interface ProductStatsJpaRepository : JpaRepository<ProductStats, Long> {
    fun findByProductIdAndDeletedAtIsNull(productId: Long): ProductStats?
}
