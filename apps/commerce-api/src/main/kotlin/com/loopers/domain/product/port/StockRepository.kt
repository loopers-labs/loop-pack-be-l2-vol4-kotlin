package com.loopers.domain.product.port

import com.loopers.domain.product.model.StockModel

interface StockRepository {
    fun save(stock: StockModel): StockModel
    fun saveAll(stocks: List<StockModel>): List<StockModel>
    fun findByProductIdOrNull(productId: Long): StockModel?
    fun findByProductIdsForUpdate(productIds: List<Long>): List<StockModel>
}
