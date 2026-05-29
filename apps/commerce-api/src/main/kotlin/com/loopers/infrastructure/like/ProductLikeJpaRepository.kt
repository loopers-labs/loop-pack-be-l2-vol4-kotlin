package com.loopers.infrastructure.like

import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeJpaRepository : JpaRepository<ProductLikeEntity, Long>, ProductLikeQueryRepository {
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean

    fun findByMemberIdAndProductId(memberId: Long, productId: Long): ProductLikeEntity?
}
