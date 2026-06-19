package com.loopers.domain.product.port

import com.loopers.domain.product.model.ProductModel

interface ProductBulkRepository {
    fun bulkInsert(products: List<ProductModel>): Int

    fun count(): Long
}
