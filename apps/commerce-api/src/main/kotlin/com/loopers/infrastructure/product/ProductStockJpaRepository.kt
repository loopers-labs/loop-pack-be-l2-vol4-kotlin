package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStockModel
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductStockJpaRepository : JpaRepository<ProductStockModel, Long> {
    fun findByProductId(productId: Long): ProductStockModel?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductStockModel s WHERE s.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductStockModel?

    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel>
}
