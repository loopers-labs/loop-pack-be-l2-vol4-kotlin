package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductModel?
    fun existsByIdAndDeletedAtIsNull(id: Long): Boolean
    fun findAllByDeletedAtIsNull(sort: Sort): List<ProductModel>
    fun findAllByBrandIdAndDeletedAtIsNull(brandId: Long, sort: Sort): List<ProductModel>

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId AND p.deletedAt IS NULL")
    fun incrementLikeCount(@Param("productId") productId: Long): Int

    @Modifying
    @Query(
        "UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 " +
            "WHERE p.id = :productId AND p.likeCount > 0 AND p.deletedAt IS NULL",
    )
    fun decrementLikeCount(@Param("productId") productId: Long): Int
}
