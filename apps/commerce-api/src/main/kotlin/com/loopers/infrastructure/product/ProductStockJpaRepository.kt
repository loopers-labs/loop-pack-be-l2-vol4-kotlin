package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStockModel
import org.springframework.data.jpa.repository.JpaRepository

interface ProductStockJpaRepository : JpaRepository<ProductStockModel, Long> {
    fun findByProductId(productId: Long): ProductStockModel?
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel>
}
