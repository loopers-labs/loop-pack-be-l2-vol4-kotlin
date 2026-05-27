package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import java.math.BigDecimal

class ProductV1Dto {
    data class ProductResponse(
        val id: Long,
        val brand: BrandResponse,
        val name: String,
        val description: String,
        val price: BigDecimal,
        val stockQuantity: Int,
        val likeCount: Int,
    ) {
        data class BrandResponse(
            val id: Long,
            val name: String,
            val description: String,
        )

        companion object {
            fun from(info: ProductInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    brand = BrandResponse(
                        id = info.brand.id,
                        name = info.brand.name,
                        description = info.brand.description,
                    ),
                    name = info.name,
                    description = info.description,
                    price = info.price,
                    stockQuantity = info.stockQuantity,
                    likeCount = info.likeCount,
                )
            }
        }
    }
}
