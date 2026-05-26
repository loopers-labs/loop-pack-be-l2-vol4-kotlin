package com.loopers.interfaces.api.like

import com.loopers.application.like.LikedProductSummary
import com.loopers.domain.common.PageResult

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

    data class LikedProductsResponse(
        val items: List<LikedProductResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(result: PageResult<LikedProductSummary>): LikedProductsResponse =
                LikedProductsResponse(
                    items = result.items.map { LikedProductResponse.from(it) },
                    page = result.page,
                    size = result.size,
                    totalElements = result.totalElements,
                    totalPages = result.totalPages,
                )
        }
    }
}
