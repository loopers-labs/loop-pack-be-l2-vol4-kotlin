package com.loopers.domain.brand

import org.springframework.data.domain.Page

interface BrandRepository {
    fun findById(brandId: Long): Brand?

    fun findDisplayable(page: Int, size: Int): Page<Brand>

    fun existsByName(name: String): Boolean

    fun save(brand: Brand): Brand

    fun update(brand: Brand): Brand
}
