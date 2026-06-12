package com.loopers.domain.product.infrastructure.persistence.stock

import com.loopers.domain.product.model.StockModel
import com.loopers.domain.product.port.StockRepository
import org.springframework.stereotype.Component

@Component
class StockRepositoryImpl(
    private val productStockJpaRepository: ProductStockJpaRepository,
) : StockRepository {
    override fun save(stock: StockModel): StockModel {
        val entity = productStockJpaRepository.findById(stock.productId)
            .map { it.also { entity -> entity.updateFrom(stock) } }
            .orElseGet { ProductStockJpaEntity.fromDomain(stock) }
        return productStockJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun saveAll(stocks: List<StockModel>): List<StockModel> = stocks.map { save(it) }

    override fun findByProductIdOrNull(productId: Long): StockModel? =
        productStockJpaRepository.findById(productId).map { it.toDomain() }.orElse(null)

    override fun findByProductIdsForUpdate(productIds: List<Long>): List<StockModel> =
        productStockJpaRepository.findByProductIdsForUpdate(productIds).map { it.toDomain() }
}
