package com.loopers.brand.domain

import com.loopers.shared.domain.CursorPage
import com.loopers.shared.domain.IdCursor

interface BrandRepository {
    fun save(brand: Brand): Brand

    fun findActiveById(id: Long): Brand?

    fun existsByName(name: BrandName): Boolean

    fun existsByNameExcludingId(name: BrandName, id: Long): Boolean

    fun findAll(cursor: IdCursor?, size: Int): CursorPage<Brand>
}
