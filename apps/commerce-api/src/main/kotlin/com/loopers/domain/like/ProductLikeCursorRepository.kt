package com.loopers.domain.like

interface ProductLikeCursorRepository {
    fun findOrCreateForUpdate(userId: Long, productId: Long): ProductLikeCursor

    fun save(cursor: ProductLikeCursor): ProductLikeCursor
}
