package com.loopers.interfaces.api.like

import com.loopers.domain.product.dto.ProductSummary

class LikeV1Dto {
    data class LikedProductResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val imageUrl: String,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(summary: ProductSummary): LikedProductResponse {
                return LikedProductResponse(
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
