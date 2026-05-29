package com.loopers.infrastructure.like

import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<LikeJpaEntity, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeJpaEntity?
}
