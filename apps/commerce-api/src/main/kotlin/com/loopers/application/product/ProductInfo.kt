package com.loopers.application.product

import com.loopers.domain.product.Product
import com.loopers.domain.stock.Stock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class ProductInfo(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val likeCount: Int,
    val soldOut: Boolean,
    val rank: Long? = null,
) {
    companion object {
        fun from(product: Product, brandName: String, stock: Stock, likeCount: Int): ProductInfo {
            return ProductInfo(
                id = product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다."),
                brandId = product.brandId,
                brandName = brandName,
                name = product.name,
                description = product.description,
                price = product.price.amount,
                stock = stock.quantity,
                likeCount = likeCount,
                soldOut = stock.isSoldOut(),
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
        fun from(product: Product, brandName: String, stock: Stock, likeCount: Int): ProductSummaryInfo {
            return ProductSummaryInfo(
                id = product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다."),
                brandId = product.brandId,
                brandName = brandName,
                name = product.name,
                price = product.price.amount,
                likeCount = likeCount,
                soldOut = stock.isSoldOut(),
            )
        }
    }
}
