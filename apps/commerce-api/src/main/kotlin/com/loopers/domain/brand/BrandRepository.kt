package com.loopers.domain.brand

interface BrandRepository {
    fun findById(brandId: Long): Brand?

    fun save(brand: Brand): Brand
}
