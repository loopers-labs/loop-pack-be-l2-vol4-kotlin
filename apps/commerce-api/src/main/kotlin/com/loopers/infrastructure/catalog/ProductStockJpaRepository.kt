package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductStockJpaRepository : JpaRepository<ProductStock, Long> {
    fun findByProductIdAndDeletedAtIsNull(productId: Long): ProductStock?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select stock
          from ProductStock stock
         where stock.productId in :productIds
           and stock.deletedAt is null
         order by stock.productId asc
        """,
    )
    fun findAllByProductIdInForUpdate(@Param("productIds") productIds: Collection<Long>): List<ProductStock>

    @Modifying
    @Query(
        """
        update ProductStock stock
           set stock.stockQuantity = stock.stockQuantity - :quantity
         where stock.productId = :productId
           and stock.deletedAt is null
           and stock.stockQuantity >= :quantity
        """,
    )
    fun deductIfEnough(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Int,
    ): Int
}
