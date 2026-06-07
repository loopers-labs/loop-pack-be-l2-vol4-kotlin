package com.loopers.infrastructure.stock

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockJpaRepository : JpaRepository<StockJpaEntity, Long> {
    fun findByProductIdAndDeletedAtIsNull(productId: Long): StockJpaEntity?

    fun findAllByProductIdInAndDeletedAtIsNull(productIds: List<Long>): List<StockJpaEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update StockJpaEntity s
        set s.quantity = s.quantity - :amount
        where s.productId = :productId and s.deletedAt is null and s.quantity >= :amount
        """,
    )
    fun deductIfEnough(
        @Param("productId") productId: Long,
        @Param("amount") amount: Int,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update StockJpaEntity s
        set s.quantity = s.quantity + :amount
        where s.productId = :productId and s.deletedAt is null
        """,
    )
    fun restore(
        @Param("productId") productId: Long,
        @Param("amount") amount: Int,
    ): Int
}
