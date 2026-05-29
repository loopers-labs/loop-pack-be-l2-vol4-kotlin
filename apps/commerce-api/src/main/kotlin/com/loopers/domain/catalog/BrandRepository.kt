package com.loopers.domain.catalog

interface BrandRepository {
    fun save(brand: Brand): Brand

    fun findById(brandId: Long): Brand?

    fun existsActiveName(name: String): Boolean
}
