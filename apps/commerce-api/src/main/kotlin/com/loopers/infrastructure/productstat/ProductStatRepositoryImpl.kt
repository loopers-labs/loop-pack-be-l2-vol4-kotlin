package com.loopers.infrastructure.productstat

import com.loopers.domain.productstat.ProductStatRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductStatRepositoryImpl(
    private val productStatJpaRepository: ProductStatJpaRepository,
) : ProductStatRepository {
    override fun findByProductId(productId: Long): com.loopers.domain.productstat.ProductStat? {
        return productStatJpaRepository.findByProductId(productId)
            ?.let(ProductStatMapper::toDomain)
    }

    override fun findAllByProductIds(productIds: Collection<Long>): List<com.loopers.domain.productstat.ProductStat> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productStatJpaRepository.findAllByProductIdIn(productIds)
            .map(ProductStatMapper::toDomain)
    }

    override fun save(
        productStat: com.loopers.domain.productstat.ProductStat,
    ): com.loopers.domain.productstat.ProductStat {
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
