package com.loopers.domain.product

import com.loopers.support.paging.PageResult

interface ProductRepository {
    fun save(product: Product): Product

    fun find(id: Long): Product?

    fun findAll(condition: ProductSearchCondition): PageResult<Product>

    fun delete(id: Long)

    fun findAllByIds(ids: List<Long>): List<Product>

    fun findActiveIdsByBrandId(brandId: Long): List<Long>
}
