package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class StockRepositoryAdapter(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepositoryPort {
    override fun findByProductId(productId: Long): Stock? =
        stockJpaRepository.findByProductId(productId)?.toDomain()

    override fun save(stock: Stock): Stock {
        val entity = if (stock.id == 0L) {
            StockEntity.from(stock)
        } else {
            stockJpaRepository.findById(stock.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.") }
                .apply { updateQuantity(stock.quantity) }
        }
        return stockJpaRepository.save(entity).toDomain()
    }

    override fun delete(stock: Stock) {
        if (stock.id == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "ID가 없는 재고는 삭제할 수 없습니다.")
        }
        val entity = stockJpaRepository.findById(stock.id)
            .orElseThrow { CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.") }
        entity.delete()
        stockJpaRepository.save(entity)
    }
}
