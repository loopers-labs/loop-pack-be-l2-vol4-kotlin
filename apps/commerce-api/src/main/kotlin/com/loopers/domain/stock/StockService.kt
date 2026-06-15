package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class StockService(
    private val stockRepositoryPort: StockRepositoryPort,
) {
    fun getByProductId(productId: Long): Stock =
        stockRepositoryPort.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")

    /**
     * 차감 경로 전용. 비관적 락으로 재고를 읽어 동시 차감을 직렬화한다.
     * 호출자(application)가 트랜잭션 경계를 열어야 락이 유효하다.
     */
    fun decrease(productId: Long, quantity: Int): Stock {
        val stock = stockRepositoryPort.findByProductIdForUpdate(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
        return stockRepositoryPort.save(stock.decrease(quantity))
    }

    fun restore(productId: Long, quantity: Int): Stock {
        val stock = getByProductId(productId)
        return stockRepositoryPort.save(stock.restore(quantity))
    }
}
