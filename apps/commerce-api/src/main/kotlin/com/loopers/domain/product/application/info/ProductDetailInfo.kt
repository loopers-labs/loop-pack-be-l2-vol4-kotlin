package com.loopers.domain.product.application.info

import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.product.model.ProductModel

data class ProductDetailInfo(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
    val name: String,
    val price: Long,
    val saleType: String,
    val rank: Long? = null,
) {
    companion object {
        fun from(
            product: ProductModel,
            brand: BrandModel,
            likeCount: Long,
            rank: Long? = null,
        ): ProductDetailInfo = ProductDetailInfo(
            id = product.id,
            brandId = product.brandId,
            brandName = brand.name.value,
            likeCount = likeCount,
            name = product.name.value,
            price = product.price.value,
            saleType = product.saleType.name,
            rank = rank,
        )
    }
}
