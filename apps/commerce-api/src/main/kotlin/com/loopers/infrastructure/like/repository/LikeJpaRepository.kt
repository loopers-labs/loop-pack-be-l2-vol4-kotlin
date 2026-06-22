package com.loopers.infrastructure.like.repository

import com.loopers.infrastructure.like.entity.LikeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<LikeEntity, Long> {
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean

    fun findByMemberIdAndProductId(memberId: Long, productId: Long): LikeEntity?

    fun findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId: Long): List<LikeEntity>
}
