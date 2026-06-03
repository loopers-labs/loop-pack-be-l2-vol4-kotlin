package com.loopers.domain.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product

data class LikedProductSummary(
    val productId: Long,
    val name: String,
    val price: Long,
    val brandName: String,
) {
    companion object {
        fun of(product: Product, brand: Brand): LikedProductSummary = LikedProductSummary(
            productId = product.id,
            name = product.name,
            price = product.price,
            brandName = brand.name,
        )
    }
}
