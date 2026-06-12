package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import org.springframework.stereotype.Component

@Component
class ProductStockRepositoryImpl(
    private val productStockJpaRepository: ProductStockJpaRepository,
) : ProductStockRepository {
    override fun save(stock: ProductStockModel): ProductStockModel {
        return productStockJpaRepository.save(stock)
    }

    override fun findByProductId(productId: Long): ProductStockModel? {
        return productStockJpaRepository.findByProductId(productId)
    }

    override fun findByProductIdForUpdate(productId: Long): ProductStockModel? {
        return productStockJpaRepository.findByProductIdForUpdate(productId)
    }

    override fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel> {
        return productStockJpaRepository.findAllByProductIdIn(productIds)
    }
}
