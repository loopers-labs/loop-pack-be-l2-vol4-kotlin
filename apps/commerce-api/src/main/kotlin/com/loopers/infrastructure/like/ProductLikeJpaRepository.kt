package com.loopers.infrastructure.like

import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeJpaRepository : JpaRepository<ProductLikeEntity, Long> {
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean
}
