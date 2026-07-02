package com.loopers.infrastructure.product.repository

import com.loopers.domain.product.ProductStatProjection
import com.loopers.domain.product.ProductStatProjectionRepository
import com.loopers.infrastructure.product.entity.ProductStatProjectionEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductStatProjectionRepositoryImpl(
    private val productStatProjectionJpaRepository: ProductStatProjectionJpaRepository,
) : ProductStatProjectionRepository {
    override fun findByProductIdForUpdate(productId: Long): ProductStatProjection? {
        return productStatProjectionJpaRepository.findByProductIdForUpdate(productId)
            ?.toDomain()
    }

    override fun save(productStatProjection: ProductStatProjection): ProductStatProjection {
        val entity = if (productStatProjection.id == 0L) {
            ProductStatProjectionEntity(
                productId = productStatProjection.productId,
                brandId = productStatProjection.brandId,
                likeCount = productStatProjection.likeCount,
                salesCount = productStatProjection.salesCount,
                latestEventVersion = productStatProjection.latestEventVersion,
            )
        } else {
            productStatProjectionJpaRepository.findByIdOrNull(productStatProjection.id)
                ?.also { it.update(productStatProjection) }
                ?: throw IllegalStateException("Product stat projection not found.")
        }

        return productStatProjectionJpaRepository.save(entity)
            .toDomain()
    }
}
