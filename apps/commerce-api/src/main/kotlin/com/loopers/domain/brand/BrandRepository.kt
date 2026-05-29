package com.loopers.domain.brand

import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor

interface BrandRepository {
    fun save(brand: Brand): Brand

    fun findActiveById(id: Long): Brand?

    fun existsByName(name: BrandName): Boolean

    fun existsByNameExcludingId(name: BrandName, id: Long): Boolean

    fun findAll(cursor: IdCursor?, size: Int): CursorPage<Brand>
}
