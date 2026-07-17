package com.loopers.infrastructure.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductJpaEntity?

    fun findByIdInAndDeletedAtIsNull(ids: List<Long>): List<ProductJpaEntity>

    fun findByBrandIdAndDeletedAtIsNull(brandId: Long): List<ProductJpaEntity>
}
