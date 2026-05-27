package com.loopers.domain.product

interface ProductRepository {
    fun save(product: ProductModel): ProductModel
    fun findActiveById(id: Long): ProductModel?
    fun findActiveAll(brandId: Long?, sort: ProductSort): List<ProductModel>
    fun existsActiveById(id: Long): Boolean
}
