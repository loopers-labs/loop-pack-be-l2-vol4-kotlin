package com.loopers.projection.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeCountQueryRepository : JpaRepository<ProductLikeCountProjectionEntity, Long> {
    fun findByProductIdIn(productIds: List<Long>): List<ProductLikeCountProjectionEntity>
}
