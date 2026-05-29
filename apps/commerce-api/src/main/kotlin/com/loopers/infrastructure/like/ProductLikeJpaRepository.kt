package com.loopers.infrastructure.like

import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeJpaRepository : JpaRepository<ProductLikeEntity, Long> {
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean

    fun findByMemberIdAndProductId(memberId: Long, productId: Long): ProductLikeEntity?

    fun findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId: Long): List<ProductLikeEntity>
}
