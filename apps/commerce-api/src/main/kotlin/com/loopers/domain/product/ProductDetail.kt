package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel

data class ProductDetail(
    val product: ProductModel,
    val brand: BrandModel,
)
