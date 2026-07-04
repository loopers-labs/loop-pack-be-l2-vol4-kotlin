package com.loopers.projection.like.infrastructure.persistence

import com.loopers.projection.like.port.ProductLikeCountProjectionRepository
import com.loopers.projection.like.port.ProductMetricsUpdateStatus
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class ProductLikeCountProjectionRepositoryImpl(
    private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
) : ProductLikeCountProjectionRepository {
    override fun increment(productId: Long): Int =
        productLikeCountJpaRepository.increment(productId)

    override fun decrement(productId: Long): Int =
        productLikeCountJpaRepository.decrement(productId)

    override fun applyDelta(
        productId: Long,
        likeDelta: Int,
        salesDelta: Int,
        viewDelta: Int,
        occurredAt: ZonedDateTime,
    ): ProductMetricsUpdateStatus {
        val updatedRows = productLikeCountJpaRepository.applyDelta(
            productId = productId,
            likeDelta = likeDelta,
            salesDelta = salesDelta,
            viewDelta = viewDelta,
            occurredAt = occurredAt,
        )
        if (updatedRows == 1) {
            return ProductMetricsUpdateStatus.APPLIED
        }
        if (!productLikeCountJpaRepository.existsById(productId)) {
            return ProductMetricsUpdateStatus.MISSING
        }
        val freshCandidates = productLikeCountJpaRepository.countFreshDeltaCandidates(
            productId = productId,
            likeDelta = likeDelta,
            salesDelta = salesDelta,
            viewDelta = viewDelta,
            occurredAt = occurredAt,
        )
        return if (freshCandidates > 0) ProductMetricsUpdateStatus.INVALID else ProductMetricsUpdateStatus.STALE
    }
}
