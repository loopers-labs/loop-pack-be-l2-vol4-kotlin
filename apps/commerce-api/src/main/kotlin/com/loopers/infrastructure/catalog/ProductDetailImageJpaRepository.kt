package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductDetailImage
import org.springframework.data.jpa.repository.JpaRepository

interface ProductDetailImageJpaRepository : JpaRepository<ProductDetailImage, Long> {
    fun findAllByProductIdAndDeletedAtIsNullOrderBySortOrderAsc(productId: Long): List<ProductDetailImage>
}
