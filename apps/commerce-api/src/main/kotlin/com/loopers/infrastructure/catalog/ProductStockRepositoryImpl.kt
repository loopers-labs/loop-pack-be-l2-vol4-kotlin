package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStockRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductStockRepositoryImpl(
    private val productStockJpaRepository: ProductStockJpaRepository,
) : ProductStockRepository {
    override fun save(stock: ProductStock): ProductStock = productStockJpaRepository.save(stock)

    override fun findByProductId(productId: Long): ProductStock? =
        productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId)

    override fun lockAllByProductIds(productIds: Collection<Long>): List<ProductStock> =
        productStockJpaRepository.findAllByProductIdInForUpdate(productIds)

    @Transactional
    override fun deductIfEnough(productId: Long, quantity: Int): Boolean =
        productStockJpaRepository.deductIfEnough(productId, quantity) == 1

    @Transactional
    override fun reserveIfAvailable(productId: Long, quantity: Int): Boolean =
        productStockJpaRepository.reserveIfAvailable(productId, quantity) == 1

    @Transactional
    override fun confirmReserved(productId: Long, quantity: Int): Boolean =
        productStockJpaRepository.confirmReserved(productId, quantity) == 1

    @Transactional
    override fun releaseReserved(productId: Long, quantity: Int): Boolean =
        productStockJpaRepository.releaseReserved(productId, quantity) == 1

    @Transactional
    override fun restoreActualStock(productId: Long, quantity: Int): Boolean =
        productStockJpaRepository.restoreActualStock(productId, quantity) == 1
}
