package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeHistory
import com.loopers.domain.like.ProductLikeHistoryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductLikeHistoryRepositoryImpl(
    private val productLikeHistoryJpaRepository: ProductLikeHistoryJpaRepository,
) : ProductLikeHistoryRepository {
    override fun findById(id: Long): ProductLikeHistory? =
        productLikeHistoryJpaRepository.findByIdOrNull(id)

    override fun findLatest(userId: Long, productId: Long): ProductLikeHistory? =
        productLikeHistoryJpaRepository.findTopByUserIdAndProductIdOrderByCreatedAtDescIdDesc(userId, productId)

    override fun save(history: ProductLikeHistory): ProductLikeHistory =
        productLikeHistoryJpaRepository.save(history)

    override fun findLikedProductIds(userId: Long, productIds: Collection<Long>): Set<Long> =
        productLikeHistoryJpaRepository.findLikedProductIds(userId, productIds)
}
