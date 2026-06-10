package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: BrandModel): BrandModel
    fun findById(id: Long): BrandModel?
    fun findActiveById(id: Long): BrandModel?
    fun existsActiveById(id: Long): Boolean
}
