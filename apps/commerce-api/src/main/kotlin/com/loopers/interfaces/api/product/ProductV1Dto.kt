package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
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
        val rank: Long?,
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
                    rank = info.rank,
                )
            }
        }
    }

    data class ProductPageResponse(
        val items: List<ProductResponse>,
        val page: Int,
        val size: Int,
        val totalCount: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(info: ProductPageInfo): ProductPageResponse {
                return ProductPageResponse(
                    items = info.items.map { ProductResponse.from(it) },
                    page = info.page,
                    size = info.size,
                    totalCount = info.totalCount,
                    totalPages = info.totalPages,
                )
            }
        }
    }
}
