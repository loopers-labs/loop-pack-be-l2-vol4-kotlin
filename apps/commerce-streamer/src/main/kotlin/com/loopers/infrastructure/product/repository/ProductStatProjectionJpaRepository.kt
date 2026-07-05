package com.loopers.infrastructure.product.repository

import com.loopers.infrastructure.product.entity.ProductStatProjectionEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductStatProjectionJpaRepository : JpaRepository<ProductStatProjectionEntity, Long> {
    fun findByProductId(productId: Long): ProductStatProjectionEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select productStat from ProductStatProjectionEntity productStat where productStat.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductStatProjectionEntity?
}
