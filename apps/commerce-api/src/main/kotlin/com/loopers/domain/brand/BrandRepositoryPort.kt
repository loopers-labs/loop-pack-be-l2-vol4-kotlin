package com.loopers.domain.brand

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface BrandRepositoryPort {
    fun findByIdOrNull(id: Long): Brand?
    fun findAll(pageRequest: PageRequest): PageResult<Brand>
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, excludeId: Long): Boolean
    fun save(brand: Brand): Brand
    fun delete(brand: Brand)
}
