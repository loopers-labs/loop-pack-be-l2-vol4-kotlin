package com.loopers.infrastructure.productstat

import org.springframework.data.jpa.repository.JpaRepository

interface ProductStatJpaRepository : JpaRepository<ProductStat, Long> {
    fun findByProductId(productId: Long): ProductStat?

    fun findAllByProductIdIn(productIds: Collection<Long>): List<ProductStat>
}
