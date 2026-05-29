package com.loopers.interfaces.api.admin.product

import com.loopers.domain.product.dto.ProductSummary

class AdminProductV1Dto {
    data class ProductSummaryResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val imageUrl: String,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(summary: ProductSummary): ProductSummaryResponse {
                return ProductSummaryResponse(
                    productId = summary.productId,
                    productName = summary.productName,
                    price = summary.price,
                    imageUrl = summary.imageUrl,
                    brandId = summary.brandId,
                    brandName = summary.brandName,
                    likeCount = summary.likeCount,
                )
            }
        }
    }
}
