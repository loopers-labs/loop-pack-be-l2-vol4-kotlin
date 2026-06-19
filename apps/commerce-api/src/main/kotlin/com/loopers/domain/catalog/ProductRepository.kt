package com.loopers.domain.catalog

interface ProductRepository {
    fun save(product: Product): Product

    fun findById(productId: Long): Product?

    fun existsActiveNameInBrand(brandId: Long, name: String): Boolean
}
