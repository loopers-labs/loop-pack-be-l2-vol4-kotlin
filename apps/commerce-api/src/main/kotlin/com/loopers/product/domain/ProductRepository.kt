package com.loopers.product.domain

import com.loopers.shared.domain.Cursor
import com.loopers.shared.domain.CursorPage

interface ProductRepository {
    fun save(product: Product): Product

    fun findActiveById(id: Long): Product?

    fun findAllActiveByIdIn(ids: List<Long>): List<Product>

    fun findActiveByBrandId(brandId: Long): List<Product>

    fun findAll(sort: ProductSort, brandId: Long?, cursor: Cursor?, size: Int): CursorPage<Product>

    fun increaseLikeCount(productId: Long)

    fun decreaseLikeCount(productId: Long)
}
