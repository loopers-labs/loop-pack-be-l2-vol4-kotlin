package com.loopers.infrastructure.product.repository

import com.loopers.domain.product.model.ProductStat
import com.loopers.domain.product.repository.ProductStatRepository
import com.loopers.infrastructure.product.mapper.ProductStatMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductStatRepositoryImpl(
    private val productStatJpaRepository: ProductStatJpaRepository,
) : ProductStatRepository {
    override fun findByProductId(productId: Long): ProductStat? {
        return productStatJpaRepository.findByProductId(productId)
            ?.let(ProductStatMapper::toDomain)
    }

    override fun findByProductIdForUpdate(productId: Long): ProductStat? {
        return productStatJpaRepository.findByProductIdForUpdate(productId)
            ?.let(ProductStatMapper::toDomain)
    }

    override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productStatJpaRepository.findAllByProductIdIn(productIds)
            .map(ProductStatMapper::toDomain)
    }

    override fun save(
        productStat: ProductStat,
    ): ProductStat {
        val entity = if (productStat.id == 0L) {
            ProductStatMapper.toEntity(productStat)
        } else {
            productStatJpaRepository.findByIdOrNull(productStat.id)
                ?.also { it.update(productStat) }
                ?: throw CoreException(ErrorType.NOT_FOUND, "Product stat not found.")
        }

        return productStatJpaRepository.save(entity)
            .let(ProductStatMapper::toDomain)
    }
}
