package com.loopers.application.order

interface CatalogStockPort {
    fun lockStocks(productIds: Collection<Long>): List<StockRow>

    fun deductAll(quantitiesByProductId: Map<Long, Int>)

    fun restoreAll(quantitiesByProductId: Map<Long, Int>)

    data class StockRow(
        val productId: Long,
        val stockQuantity: Int,
    )
}
