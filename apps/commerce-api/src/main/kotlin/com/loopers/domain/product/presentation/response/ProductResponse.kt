package com.loopers.domain.product.presentation.response

import com.loopers.domain.product.application.info.ProductDetailInfo
import com.loopers.domain.product.application.info.ProductInfo
import com.loopers.domain.product.application.info.ProductSummaryInfo

data class ProductResponse(
    val id: Long,
    val brandId: Long,
    val brandName: String? = null,
    val likeCount: Long = 0,
    val name: String,
    val price: Long,
) {
    companion object {
        fun from(info: ProductInfo): ProductResponse = ProductResponse(
            id = info.id,
            brandId = info.brandId,
            name = info.name,
            price = info.price,
        )

        fun from(info: ProductDetailInfo): ProductResponse = ProductResponse(
            id = info.id,
            brandId = info.brandId,
            brandName = info.brandName,
            likeCount = info.likeCount,
            name = info.name,
            price = info.price,
        )

        fun from(info: ProductSummaryInfo): ProductResponse = ProductResponse(
            id = info.id,
            brandId = info.brandId,
            brandName = info.brandName,
            likeCount = info.likeCount,
            name = info.name,
            price = info.price,
        )
    }
}
