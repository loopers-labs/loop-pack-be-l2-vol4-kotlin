package com.loopers.application.productstat

import com.loopers.domain.product.model.ProductStat
import com.loopers.domain.product.repository.ProductStatRepository
import org.springframework.stereotype.Component

@Component
class ProductStatService(
    private val productStatRepository: ProductStatRepository,
) {
    fun getProductStat(productId: Long): ProductStat {
        return productStatRepository.findByProductId(productId)
            ?: emptyStat(productId)
    }

    fun getProductStatForUpdate(productId: Long): ProductStat {
        return productStatRepository.findByProductIdForUpdate(productId)
            ?: emptyStat(productId)
    }

    fun getProductStats(productIds: Collection<Long>): List<ProductStat> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productStatRepository.findAllByProductIds(productIds)
    }

    fun emptyStat(productId: Long): ProductStat {
        return ProductStat(productId = productId, likeCount = 0)
    }

    fun save(productStat: ProductStat): ProductStat {
        return productStatRepository.save(productStat)
    }
}
