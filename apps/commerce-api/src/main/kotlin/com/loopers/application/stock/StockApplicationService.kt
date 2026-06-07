package com.loopers.application.stock

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class StockApplicationService(
    private val stockRepository: StockRepository,
) {
    @Transactional
    fun createStock(productId: Long, initialQuantity: Int): Stock {
        return stockRepository.save(
            Stock(productId = productId, quantity = initialQuantity),
        )
    }

    fun getStock(productId: Long): Stock {
        return stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다. productId=$productId")
    }

    fun getStocks(productIds: List<Long>): Map<Long, Stock> {
        return stockRepository.findAllByProductIds(productIds)
            .associateBy { it.productId }
    }

    @Transactional
    fun deduct(productId: Long, amount: Int) {
        val stock = getStock(productId)
        stock.validateDeductible(amount)

        if (!stockRepository.deductIfEnough(productId = productId, amount = amount)) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.")
        }
    }

    @Transactional
    fun restore(productId: Long, amount: Int) {
        if (!stockRepository.restore(productId = productId, amount = amount)) {
            throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다. productId=$productId")
        }
    }
}
