package com.loopers.infrastructure.productstat

import org.springframework.data.jpa.repository.JpaRepository

interface ProductStatJpaRepository : JpaRepository<ProductStatEntity, Long> {
    fun findByProductId(productId: Long): ProductStatEntity?

    fun findAllByProductIdIn(productIds: Collection<Long>): List<ProductStatEntity>
}
