package com.loopers.interfaces.api.product

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.product.ProductDetail
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductSummary

interface ProductApplicationServicePort {
    fun getProduct(id: Long): ProductDetail
    fun getProducts(brandId: Long?, sort: ProductSort, pageRequest: PageRequest): PageResult<ProductSummary>
}
