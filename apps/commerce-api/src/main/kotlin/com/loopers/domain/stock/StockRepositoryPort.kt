package com.loopers.domain.stock

interface StockRepositoryPort {
    fun findByProductId(productId: Long): Stock?
    fun save(stock: Stock): Stock
    fun delete(stock: Stock)
}
