package com.loopers.interfaces.api.like

import com.loopers.domain.like.LikedProductSummary

class LikeV1Dto {
    data class LikedProductResponse(
        val productId: Long,
        val name: String,
        val price: Long,
        val brandName: String,
    ) {
        companion object {
            fun from(summary: LikedProductSummary): LikedProductResponse =
                LikedProductResponse(
                    productId = summary.productId,
                    name = summary.name,
                    price = summary.price,
                    brandName = summary.brandName,
                )
        }
    }
}
