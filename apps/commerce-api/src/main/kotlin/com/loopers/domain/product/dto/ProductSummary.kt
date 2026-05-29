package com.loopers.domain.product.dto

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.productstat.ProductStat

data class ProductSummary(
    val productId: Long,
    val productName: String,
    val price: Long,
    val imageUrl: String,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
) {
    companion object {
        fun from(
            product: Product,
            brand: Brand,
            productStat: ProductStat,
        ): ProductSummary {
            return ProductSummary(
                productId = product.id,
                productName = product.name,
                price = product.price,
                imageUrl = product.imageUrl,
                brandId = brand.id,
                brandName = brand.name,
                likeCount = productStat.likeCount,
            )
        }
    }
}
