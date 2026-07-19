package com.loopers.infrastructure.product.repository

import com.loopers.infrastructure.product.entity.ProductStatEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductStatJpaRepository : JpaRepository<ProductStatEntity, Long> {
    fun findByProductId(productId: Long): ProductStatEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select productStat from ProductStatEntity productStat where productStat.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductStatEntity?
}
