package com.loopers.domain.product.port

import com.loopers.domain.product.model.ProductModel

interface ProductRepository {
    fun save(product: ProductModel): ProductModel
    fun saveAll(products: List<ProductModel>): List<ProductModel>
    fun findById(productId: Long): ProductModel?
    fun findAllByIds(productIds: Collection<Long>): List<ProductModel>
    fun findByBrandId(brandId: Long): List<ProductModel>
    fun findByCondition(condition: ProductSearchCondition): List<ProductModel>
}
