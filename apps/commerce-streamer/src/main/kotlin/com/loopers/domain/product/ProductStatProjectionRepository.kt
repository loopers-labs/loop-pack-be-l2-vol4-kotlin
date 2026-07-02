package com.loopers.domain.product

interface ProductStatProjectionRepository {
    fun findByProductIdForUpdate(productId: Long): ProductStatProjection?

    fun save(productStatProjection: ProductStatProjection): ProductStatProjection
}
