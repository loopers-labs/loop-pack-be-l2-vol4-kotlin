package com.loopers.domain.brand

interface BrandRepository {
    fun findById(brandId: Long): Brand?

    fun existsByName(name: String): Boolean

    fun save(brand: Brand): Brand
}
