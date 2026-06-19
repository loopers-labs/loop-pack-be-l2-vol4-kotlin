package com.loopers.application.catalog.port

interface CatalogProductStatsCommandPort {
    fun increaseLikeCount(productId: Long)

    fun decreaseLikeCount(productId: Long)
}
