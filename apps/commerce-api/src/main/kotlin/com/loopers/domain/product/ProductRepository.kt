package com.loopers.domain.product

import com.loopers.support.paging.PageResult

interface ProductRepository {
    fun save(product: Product): Product

    fun find(id: Long): Product?

    fun findAll(condition: ProductSearchCondition): PageResult<Product>

    fun increaseLikeCount(id: Long): Boolean

    fun decreaseLikeCount(id: Long): Boolean

    fun delete(id: Long)
}
