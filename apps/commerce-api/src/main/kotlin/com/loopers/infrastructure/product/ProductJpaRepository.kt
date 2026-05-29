package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductStatus
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndStatusNot(id: Long, status: ProductStatus): Product?

    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.likeCount = p.likeCount + 1 where p.id = :id")
    fun incrementLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.likeCount = case when p.likeCount > 0 then p.likeCount - 1 else 0 end where p.id = :id")
    fun decrementLikeCount(@Param("id") id: Long): Int

    fun findByBrandIdAndStatusNot(brandId: Long, status: ProductStatus): List<Product>

    fun findByStatusNot(
        status: ProductStatus,
        scrollPosition: ScrollPosition,
        limit: Limit,
        sort: Sort,
    ): Window<Product>

    fun findByBrandIdAndStatusNot(
        brandId: Long,
        status: ProductStatus,
        scrollPosition: ScrollPosition,
        limit: Limit,
        sort: Sort,
    ): Window<Product>
}
