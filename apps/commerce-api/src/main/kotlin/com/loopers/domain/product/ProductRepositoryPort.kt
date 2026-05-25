package com.loopers.domain.product

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface ProductRepositoryPort {
    fun findByIdOrNull(id: Long): Product?
    fun findAll(pageRequest: PageRequest): PageResult<Product>
    fun findAllByBrandId(brandId: Long, pageRequest: PageRequest): PageResult<Product>
    fun findAllByBrandId(brandId: Long): List<Product>
    fun save(product: Product): Product
    fun delete(product: Product)
}
