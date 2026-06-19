package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {
    override fun save(stock: Stock): Stock {
        val entity = stock.id
            ?.let { id -> stockJpaRepository.findByIdOrNull(id) }
            ?.also { it.updateFrom(stock) }
            ?: StockJpaEntity.from(stock)

        return stockJpaRepository.save(entity).toDomain()
    }

    override fun findByProductId(productId: Long): Stock? {
        return stockJpaRepository.findByProductIdAndDeletedAtIsNull(productId)
            ?.toDomain()
    }

    override fun findAllByProductIds(productIds: List<Long>): List<Stock> {
        return stockJpaRepository.findAllByProductIdInAndDeletedAtIsNull(productIds)
            .map { it.toDomain() }
    }

    override fun deductIfEnough(productId: Long, amount: Int): Boolean {
        return stockJpaRepository.deductIfEnough(productId = productId, amount = amount) == 1
    }

    override fun restore(productId: Long, amount: Int): Boolean {
        return stockJpaRepository.restore(productId = productId, amount = amount) == 1
    }

    override fun deleteByProductId(productId: Long) {
        stockJpaRepository.findByProductIdAndDeletedAtIsNull(productId)
            ?.delete()
    }
}
