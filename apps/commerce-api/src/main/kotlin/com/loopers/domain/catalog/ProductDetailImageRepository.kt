package com.loopers.domain.catalog

interface ProductDetailImageRepository {
    fun saveAll(images: List<ProductDetailImage>): List<ProductDetailImage>

    fun findByProductId(productId: Long): List<ProductDetailImage>

    fun softDeleteByProductId(productId: Long)
}
