package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.Product
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class ProductInfo(
    val id: Long,
    val brandId: Long,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val likeCount: Int,
    val soldOut: Boolean,
) {
    companion object {
        fun from(product: Product): ProductInfo {
            return ProductInfo(
                id = product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다."),
                brandId = product.brandId,
                name = product.name,
                description = product.description,
                price = product.price.amount,
                stock = product.stock.value,
                likeCount = product.likeCount,
                soldOut = product.isSoldOut(),
            )
        }
    }
}

data class ProductSummaryInfo(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val name: String,
    val price: Long,
    val likeCount: Int,
    val soldOut: Boolean,
) {
    companion object {
        fun from(product: Product, brandName: String): ProductSummaryInfo {
            return ProductSummaryInfo(
                id = product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다."),
                brandId = product.brandId,
                brandName = brandName,
                name = product.name,
                price = product.price.amount,
                likeCount = product.likeCount,
                soldOut = product.isSoldOut(),
            )
        }
    }
}

data class ProductDetailInfo(
    val product: ProductInfo,
    val brand: BrandInfo,
)
