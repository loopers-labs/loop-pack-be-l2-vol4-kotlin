package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: Brand): Brand

    fun find(id: Long): Brand?

    fun findAll(ids: Collection<Long>): List<Brand>

    fun delete(id: Long)
}
