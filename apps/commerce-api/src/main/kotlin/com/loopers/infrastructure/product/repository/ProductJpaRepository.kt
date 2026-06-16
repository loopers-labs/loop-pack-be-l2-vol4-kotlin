package com.loopers.infrastructure.product.repository

import com.loopers.infrastructure.product.entity.ProductEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductEntity, Long>, ProductQueryRepository {
    fun findAllByBrandId(brandId: Long): List<ProductEntity>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean

    fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean
}
