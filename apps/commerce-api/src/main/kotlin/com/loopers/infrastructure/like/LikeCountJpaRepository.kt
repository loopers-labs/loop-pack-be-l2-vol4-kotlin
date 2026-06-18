package com.loopers.infrastructure.like

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeCountJpaRepository : JpaRepository<LikeCountEntity, Long> {
    fun findByProductId(productId: Long): LikeCountEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lc from LikeCountEntity lc where lc.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): LikeCountEntity?

    fun findAllByProductIdIn(productIds: List<Long>): List<LikeCountEntity>
    fun deleteByProductId(productId: Long)
}
