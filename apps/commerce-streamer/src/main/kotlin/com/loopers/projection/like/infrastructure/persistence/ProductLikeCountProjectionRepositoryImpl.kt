package com.loopers.projection.like.infrastructure.persistence

import com.loopers.projection.like.port.ProductLikeCountProjectionRepository
import com.loopers.projection.like.port.ProductMetricsUpdateStatus
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class ProductLikeCountProjectionRepositoryImpl(
    private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
) : ProductLikeCountProjectionRepository {
    override fun applyDelta(
        productId: Long,
        likeDelta: Int,
        salesDelta: Long,
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

        val insertedRows = productLikeCountJpaRepository.insertDeltaIfAbsent(
            productId = productId,
            likeDelta = likeDelta,
            salesDelta = salesDelta,
            viewDelta = viewDelta,
            occurredAt = occurredAt,
        )
        if (insertedRows == 1) {
            return ProductMetricsUpdateStatus.APPLIED
        }

        val retriedRows = productLikeCountJpaRepository.applyDelta(
            productId = productId,
            likeDelta = likeDelta,
            salesDelta = salesDelta,
            viewDelta = viewDelta,
            occurredAt = occurredAt,
        )
        return if (retriedRows == 1) ProductMetricsUpdateStatus.APPLIED else ProductMetricsUpdateStatus.INVALID
    }
}
