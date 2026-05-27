package com.loopers.domain.product

interface ProductStockRepository {
    fun save(stock: ProductStockModel): ProductStockModel
    fun findByProductId(productId: Long): ProductStockModel?
}
