package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductStatsJpaRepository : JpaRepository<ProductStats, Long> {
    fun findByProductIdAndDeletedAtIsNull(productId: Long): ProductStats?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProductStats stats
           set stats.likeCount = stats.likeCount + 1
         where stats.productId = :productId
           and stats.deletedAt is null
        """,
    )
    fun increaseLikeCount(@Param("productId") productId: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProductStats stats
           set stats.likeCount = stats.likeCount - 1
         where stats.productId = :productId
           and stats.deletedAt is null
           and stats.likeCount > 0
        """,
    )
    fun decreaseLikeCount(@Param("productId") productId: Long): Int
}
