package com.loopers.domain.stock

interface StockRepository {
    fun save(stock: Stock): Stock

    fun findByProductId(productId: Long): Stock?

    fun findAllByProductIds(productIds: List<Long>): List<Stock>

    fun deductIfEnough(productId: Long, amount: Int): Boolean

    fun restore(productId: Long, amount: Int): Boolean

    fun deleteByProductId(productId: Long)
}
