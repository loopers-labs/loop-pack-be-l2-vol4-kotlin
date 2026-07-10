package com.loopers.product.interfaces

import com.loopers.product.application.ProductService
import com.loopers.product.domain.ProductSort
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class ProductListQuery(
    private val productService: ProductService,
) {
    @Cacheable(cacheNames = [CACHE_NAME], key = "#sort.name() + ':' + #size", sync = true)
    fun firstPage(sort: ProductSort, size: Int): ProductListResponse {
        val page = productService.list(sort, null, null, size)
        return ProductListResponse.from(sort, page)
    }

    companion object {
        const val CACHE_NAME = "productListFirstPage"
    }
}
