package com.loopers.domain.stock

interface StockRepositoryPort {
    fun findByProductId(productId: Long): Stock?
    fun findAllByProductIdIn(productIds: List<Long>): List<Stock>
    fun save(stock: Stock): Stock
    fun delete(stock: Stock)
}
