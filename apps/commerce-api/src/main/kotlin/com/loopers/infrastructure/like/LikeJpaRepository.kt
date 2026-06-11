package com.loopers.infrastructure.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LikeJpaRepository : JpaRepository<LikeEntity, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeEntity?
    fun findAllByUserId(userId: Long, pageable: Pageable): Page<LikeEntity>
    fun deleteAllByProductId(productId: Long)

    @Query("select l.productId from LikeEntity l where l.userId = :userId")
    fun findAllProductIdsByUserId(userId: Long): List<Long>
}
