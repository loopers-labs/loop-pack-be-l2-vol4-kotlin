package com.loopers.infrastructure.like

import org.springframework.data.jpa.repository.JpaRepository

interface LikeCountJpaRepository : JpaRepository<LikeCountEntity, Long> {
    fun findByProductId(productId: Long): LikeCountEntity?
    fun deleteByProductId(productId: Long)
}
