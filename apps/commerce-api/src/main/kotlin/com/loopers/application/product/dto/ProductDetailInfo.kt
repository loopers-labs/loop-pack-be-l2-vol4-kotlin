package com.loopers.application.product.dto

import com.loopers.application.brand.dto.BrandInfo
import com.loopers.domain.product.dto.ProductCatalog

data class ProductDetailInfo(
    val productId: Long,
    val productName: String,
    val price: Long,
    val description: String,
    val imageUrl: String,
    val brand: BrandInfo,
    val likeCount: Long,
    val quantity: Long? = null,
) {
    companion object {
        fun from(productCatalog: ProductCatalog): ProductDetailInfo {
            val product = productCatalog.product
            return ProductDetailInfo(
                productId = product.id,
                productName = product.name,
                price = product.price,
                description = product.description,
                imageUrl = product.imageUrl,
                brand = BrandInfo.from(productCatalog.brand),
                likeCount = productCatalog.productStat.likeCount,
                quantity = productCatalog.inventory?.quantity,
            )
        }
    }
}
