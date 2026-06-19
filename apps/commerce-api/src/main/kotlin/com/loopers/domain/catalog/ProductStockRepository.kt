package com.loopers.domain.catalog

interface ProductStockRepository {
    fun save(stock: ProductStock): ProductStock

    fun findByProductId(productId: Long): ProductStock?

    fun lockAllByProductIds(productIds: Collection<Long>): List<ProductStock>

    fun deductIfEnough(productId: Long, quantity: Int): Boolean

    fun reserveIfAvailable(productId: Long, quantity: Int): Boolean

    fun confirmReserved(productId: Long, quantity: Int): Boolean

    fun releaseReserved(productId: Long, quantity: Int): Boolean

    fun restoreActualStock(productId: Long, quantity: Int): Boolean
}
