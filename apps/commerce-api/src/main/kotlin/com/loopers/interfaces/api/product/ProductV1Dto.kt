package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductDetail

class ProductV1Dto {
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
}
