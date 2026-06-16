package com.loopers.domain.brand.repository

import com.loopers.domain.brand.model.Brand
import org.springframework.data.domain.Page

interface BrandRepository {
    fun findById(brandId: Long): Brand?

    fun findAllByIds(brandIds: Collection<Long>): List<Brand>

    fun findDisplayable(page: Int, size: Int): Page<Brand>

    fun existsByName(name: String): Boolean

    fun save(brand: Brand): Brand

    fun update(brand: Brand): Brand
}
