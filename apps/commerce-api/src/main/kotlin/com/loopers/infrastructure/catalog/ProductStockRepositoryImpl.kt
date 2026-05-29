package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStockRepository
import org.springframework.stereotype.Component

@Component
class ProductStockRepositoryImpl(
    private val productStockJpaRepository: ProductStockJpaRepository,
) : ProductStockRepository {
    override fun save(stock: ProductStock): ProductStock = productStockJpaRepository.save(stock)

    override fun findByProductId(productId: Long): ProductStock? =
        productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId)

    override fun lockAllByProductIds(productIds: Collection<Long>): List<ProductStock> =
        productStockJpaRepository.findAllByProductIdInForUpdate(productIds)

    override fun deductIfEnough(productId: Long, quantity: Int): Boolean =
        productStockJpaRepository.deductIfEnough(productId, quantity) == 1
}
