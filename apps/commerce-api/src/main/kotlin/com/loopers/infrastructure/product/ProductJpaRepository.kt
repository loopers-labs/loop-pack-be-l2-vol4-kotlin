package com.loopers.infrastructure.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    fun findAllByBrandId(brandId: Long, pageable: Pageable): Page<ProductEntity>
    fun findAllByBrandId(brandId: Long): List<ProductEntity>
}
