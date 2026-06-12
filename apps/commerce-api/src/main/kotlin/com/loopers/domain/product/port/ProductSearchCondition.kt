package com.loopers.domain.product.port

import com.loopers.domain.product.vo.ProductSort

data class ProductSearchCondition(
    val brandId: Long?,
    val sort: ProductSort,
    val page: Int,
    val size: Int,
)
