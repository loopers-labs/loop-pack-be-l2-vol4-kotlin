package com.loopers.application.product

import com.loopers.domain.product.ProductDetail
import java.math.BigDecimal

data class ProductInfo(
    val id: Long,
    val brand: Brand,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val stockQuantity: Int,
    val likeCount: Int,
) {
    data class Brand(
        val id: Long,
        val name: String,
        val description: String,
    )

    companion object {
        fun from(detail: ProductDetail): ProductInfo {
            return ProductInfo(
                id = detail.product.id,
                brand = Brand(
                    id = detail.brand.id,
                    name = detail.brand.name,
                    description = detail.brand.description,
                ),
                name = detail.product.name,
                description = detail.product.description,
                price = detail.product.price,
                stockQuantity = detail.product.stockQuantity,
                likeCount = detail.product.likeCount,
            )
        }
    }
}
