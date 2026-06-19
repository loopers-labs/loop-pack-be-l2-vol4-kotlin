package com.loopers.application.productstat

import com.loopers.domain.product.model.ProductStat
import com.loopers.domain.product.repository.ProductStatRepository
import org.springframework.stereotype.Component

@Component
class ProductStatService(
    private val productStatRepository: ProductStatRepository,
) {
    fun getProductStat(productId: Long, brandId: Long): ProductStat {
        return productStatRepository.findByProductId(productId)
            ?: emptyStat(productId = productId, brandId = brandId)
    }

    fun getProductStatForUpdate(productId: Long, brandId: Long): ProductStat {
        return productStatRepository.findByProductIdForUpdate(productId)
            ?: emptyStat(productId = productId, brandId = brandId)
    }

    fun getProductStats(productIds: Collection<Long>): List<ProductStat> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productStatRepository.findAllByProductIds(productIds)
    }

    fun emptyStat(productId: Long, brandId: Long): ProductStat {
        return ProductStat(productId = productId, brandId = brandId, likeCount = 0)
    }

    fun save(productStat: ProductStat): ProductStat {
        return productStatRepository.save(productStat)
    }

    fun increaseLikeCount(productId: Long, brandId: Long) {
        val productStat = getProductStatForUpdate(productId = productId, brandId = brandId)
        productStat.increaseLikeCount()
        save(productStat)
    }

    fun decreaseLikeCount(productId: Long, brandId: Long) {
        val productStat = getProductStatForUpdate(productId = productId, brandId = brandId)
        productStat.decreaseLikeCount()
        save(productStat)
    }
}
