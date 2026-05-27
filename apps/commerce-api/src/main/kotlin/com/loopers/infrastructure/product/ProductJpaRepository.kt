package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductModel?
    fun existsByIdAndDeletedAtIsNull(id: Long): Boolean
    fun findAllByDeletedAtIsNull(sort: Sort): List<ProductModel>
    fun findAllByBrandIdAndDeletedAtIsNull(brandId: Long, sort: Sort): List<ProductModel>
}
