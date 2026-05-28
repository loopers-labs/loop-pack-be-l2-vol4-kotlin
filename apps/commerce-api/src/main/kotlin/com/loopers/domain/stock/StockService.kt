package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class StockService(
    private val stockRepositoryPort: StockRepositoryPort,
) {
    fun getByProductId(productId: Long): Stock =
        stockRepositoryPort.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")

    fun decrease(productId: Long, quantity: Int): Stock {
        val stock = getByProductId(productId)
        return stockRepositoryPort.save(stock.decrease(quantity))
    }

    fun restore(productId: Long, quantity: Int): Stock {
        val stock = getByProductId(productId)
        return stockRepositoryPort.save(stock.restore(quantity))
    }
}
