package com.loopers.infrastructure.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    fun findAllByBrandId(brandId: Long, pageable: Pageable): Page<ProductEntity>
    fun findAllByBrandId(brandId: Long): List<ProductEntity>

    @Query(
        value = "SELECT p FROM ProductEntity p " +
            "LEFT JOIN LikeCountEntity lc ON lc.productId = p.id " +
            "WHERE (:brandId IS NULL OR p.brandId = :brandId) " +
            "ORDER BY COALESCE(lc.count, 0) DESC, p.id DESC",
        countQuery = "SELECT COUNT(p) FROM ProductEntity p WHERE (:brandId IS NULL OR p.brandId = :brandId)",
    )
    fun findAllOrderByLikesDesc(@Param("brandId") brandId: Long?, pageable: Pageable): Page<ProductEntity>
}
