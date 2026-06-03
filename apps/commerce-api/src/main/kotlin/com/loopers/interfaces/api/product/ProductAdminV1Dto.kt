package com.loopers.interfaces.api.product

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.product.UpdateProductCommand
import com.loopers.domain.product.ProductDetail
import com.loopers.domain.product.ProductSummary

class ProductAdminV1Dto {
    data class CreateProductRequest(
        val name: String,
        val price: Long,
        val description: String,
        val brandId: Long,
        val quantity: Int,
    ) {
        fun toCommand(): CreateProductCommand = CreateProductCommand(
            name = name,
            price = price,
            description = description,
            brandId = brandId,
            quantity = quantity,
        )
    }

    data class UpdateProductRequest(
        val name: String,
        val price: Long,
        val description: String,
        val brandId: Long,
        val quantity: Int,
    ) {
        fun toCommand(id: Long): UpdateProductCommand = UpdateProductCommand(
            id = id,
            name = name,
            price = price,
            description = description,
            brandId = brandId,
            quantity = quantity,
        )
    }

    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val description: String,
        val brandId: Long,
        val brandName: String,
        val stockQuantity: Int,
        val likeCount: Long,
    ) {
        companion object {
            fun from(detail: ProductDetail): ProductResponse = ProductResponse(
                id = detail.id,
                name = detail.name,
                price = detail.price,
                description = detail.description,
                brandId = detail.brandId,
                brandName = detail.brandName,
                stockQuantity = detail.stockQuantity,
                likeCount = detail.likeCount,
            )
        }
    }

    data class ProductSummaryResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val brandId: Long,
        val brandName: String,
        val stockQuantity: Int,
        val likeCount: Long,
    ) {
        companion object {
            fun from(summary: ProductSummary): ProductSummaryResponse = ProductSummaryResponse(
                id = summary.id,
                name = summary.name,
                price = summary.price,
                brandId = summary.brandId,
                brandName = summary.brandName,
                stockQuantity = summary.stockQuantity,
                likeCount = summary.likeCount,
            )
        }
    }
}
