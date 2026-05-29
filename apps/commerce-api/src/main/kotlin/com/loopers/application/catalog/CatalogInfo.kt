package com.loopers.application.catalog

import com.loopers.domain.catalog.Brand
import com.loopers.domain.catalog.BrandStatus
import com.loopers.domain.catalog.Product
import com.loopers.domain.catalog.ProductStatus

class CatalogInfo {
    data class BrandInfo(
        val brandId: Long,
        val name: String,
        val status: BrandStatus,
    ) {
        companion object {
            fun from(brand: Brand) = BrandInfo(
                brandId = brand.id,
                name = brand.name,
                status = brand.status,
            )
        }
    }

    data class ProductInfo(
        val productId: Long,
        val brandId: Long,
        val name: String,
        val price: Long,
        val status: ProductStatus,
    ) {
        companion object {
            fun from(product: Product) = ProductInfo(
                productId = product.id,
                brandId = product.brandId,
                name = product.name,
                price = product.price,
                status = product.status,
            )
        }
    }

    data class ProductDisplayRow(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Long,
        val likeCount: Long,
        val stockQuantity: Int,
    )

    data class ProductDetailRow(
        val product: ProductDisplayRow,
        val detailImages: List<String>,
    )

    data class ProductDisplayInfo(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val soldOut: Boolean,
    )

    data class ProductDetailInfo(
        val product: ProductDisplayInfo,
        val detailImages: List<String>,
    )

    data class OrderSnapshotInfo(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Long,
        val stockQuantity: Int,
        val displayable: Boolean,
    )
}
