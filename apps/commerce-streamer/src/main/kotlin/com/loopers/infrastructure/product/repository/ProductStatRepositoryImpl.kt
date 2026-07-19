package com.loopers.infrastructure.product.repository

import com.loopers.domain.product.ProductStat
import com.loopers.domain.product.ProductStatRepository
import com.loopers.infrastructure.product.entity.ProductStatEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductStatRepositoryImpl(
    private val productStatJpaRepository: ProductStatJpaRepository,
) : ProductStatRepository {
    override fun findByProductIdForUpdate(productId: Long): ProductStat? {
        return productStatJpaRepository.findByProductIdForUpdate(productId)
            ?.toDomain()
    }

    override fun save(productStat: ProductStat): ProductStat {
        val entity = if (productStat.id == 0L) {
            ProductStatEntity(
                productId = productStat.productId,
                brandId = productStat.brandId,
                likeCount = productStat.likeCount,
                salesCount = productStat.salesCount,
                viewCount = productStat.viewCount,
                latestEventVersion = productStat.latestEventVersion,
            )
        } else {
            productStatJpaRepository.findByIdOrNull(productStat.id)
                ?.also { it.update(productStat) }
                ?: throw IllegalStateException("Product stat not found.")
        }

        return productStatJpaRepository.save(entity)
            .toDomain()
    }
}
