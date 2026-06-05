package com.loopers.domain.brand.port

import com.loopers.domain.brand.model.BrandModel

interface BrandRepository {
    fun save(brand: BrandModel): BrandModel
    fun findById(brandId: Long): BrandModel?
    fun findAllByIds(brandIds: Collection<Long>): List<BrandModel>
}
