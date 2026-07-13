package com.loopers.interfaces.api.product.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.interfaces.api.brand.dto.BrandV1Dto

class ProductV1Dto {
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
            fun from(info: ProductSummary): ProductSummaryResponse {
                return ProductSummaryResponse(
                    productId = info.productId,
                    productName = info.productName,
                    price = info.price,
                    imageUrl = info.imageUrl,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    likeCount = info.likeCount,
                )
            }
        }
    }

    data class ProductDetailResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val description: String,
        val imageUrl: String,
        val brand: BrandV1Dto.BrandResponse,
        val likeCount: Long,
        @field:JsonInclude(JsonInclude.Include.ALWAYS)
        val rank: Long?,
    ) {
        companion object {
            fun from(info: ProductDetailInfo): ProductDetailResponse {
                return ProductDetailResponse(
                    productId = info.productId,
                    productName = info.productName,
                    price = info.price,
                    description = info.description,
                    imageUrl = info.imageUrl,
                    brand = BrandV1Dto.BrandResponse.from(info.brand),
                    likeCount = info.likeCount,
                    rank = info.rank,
                )
            }
        }
    }
}
