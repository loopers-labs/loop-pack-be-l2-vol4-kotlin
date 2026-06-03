package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.stock.Stock

data class ProductDetail(
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
        fun of(product: Product, brand: Brand, stock: Stock, likeCount: Long): ProductDetail = ProductDetail(
            id = product.id,
            name = product.name,
            price = product.price,
            description = product.description,
            brandId = product.brandId,
            brandName = brand.name,
            stockQuantity = stock.quantity,
            likeCount = likeCount,
        )
    }
}

data class ProductSummary(
    val id: Long,
    val name: String,
    val price: Long,
    val brandId: Long,
    val brandName: String,
    val stockQuantity: Int,
    val likeCount: Long,
) {
    companion object {
        fun of(product: Product, brand: Brand, stock: Stock, likeCount: Long): ProductSummary = ProductSummary(
            id = product.id,
            name = product.name,
            price = product.price,
            brandId = product.brandId,
            brandName = brand.name,
            stockQuantity = stock.quantity,
            likeCount = likeCount,
        )
    }
}
