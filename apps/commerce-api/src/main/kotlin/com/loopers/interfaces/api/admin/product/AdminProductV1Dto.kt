package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.dto.AdminProductDetailInfo
import com.loopers.application.product.dto.ProductCreateCommand
import com.loopers.application.product.dto.ProductUpdateCommand
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.interfaces.api.brand.BrandV1Dto

class AdminProductV1Dto {
    data class CreateProductRequest(
        val brandId: Long,
        val name: String,
        val price: Long,
        val description: String,
        val imageUrl: String,
        val quantity: Long,
    ) {
        fun toCommand(): ProductCreateCommand {
            return ProductCreateCommand(
                brandId = brandId,
                name = name,
                price = price,
                description = description,
                imageUrl = imageUrl,
                quantity = quantity,
            )
        }
    }

    data class UpdateProductRequest(
        val name: String,
        val price: Long,
        val description: String,
        val imageUrl: String,
    ) {
        fun toCommand(): ProductUpdateCommand {
            return ProductUpdateCommand(
                name = name,
                price = price,
                description = description,
                imageUrl = imageUrl,
            )
        }
    }

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

    data class ProductDetailResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val description: String,
        val imageUrl: String,
        val brand: BrandV1Dto.BrandResponse,
        val likeCount: Long,
        val quantity: Long,
    ) {
        companion object {
            fun from(info: AdminProductDetailInfo): ProductDetailResponse {
                return ProductDetailResponse(
                    productId = info.productId,
                    productName = info.productName,
                    price = info.price,
                    description = info.description,
                    imageUrl = info.imageUrl,
                    brand = BrandV1Dto.BrandResponse.from(info.brand),
                    likeCount = info.likeCount,
                    quantity = info.quantity,
                )
            }
        }
    }
}
