package com.loopers.infrastructure.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductJpaEntity?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProductJpaEntity p
        set p.likeCount = p.likeCount + 1
        where p.id = :id and p.deletedAt is null
        """,
    )
    fun increaseLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProductJpaEntity p
        set p.likeCount = p.likeCount - 1
        where p.id = :id and p.deletedAt is null and p.likeCount > 0
        """,
    )
    fun decreaseLikeCountIfPositive(@Param("id") id: Long): Int
}
