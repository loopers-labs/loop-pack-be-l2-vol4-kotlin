package com.loopers.application.product

import com.loopers.domain.product.ProductSort

interface ProductCacheRepository {
    fun getDetail(productId: Long): ProductInfo?
    fun putDetail(productId: Long, product: ProductInfo)
    fun evictDetail(productId: Long)
    fun getList(query: ProductListCacheQuery): ProductPageInfo?
    fun putList(query: ProductListCacheQuery, products: ProductPageInfo)

    data class ProductListCacheQuery(
        val brandId: Long?,
        val sort: ProductSort,
        val page: Int,
        val size: Int,
    )
}
