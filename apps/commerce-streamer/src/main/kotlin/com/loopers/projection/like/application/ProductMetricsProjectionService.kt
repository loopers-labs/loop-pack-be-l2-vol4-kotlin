package com.loopers.projection.like.application

import com.loopers.projection.like.port.ProcessedKafkaEventRepository
import com.loopers.projection.like.port.ProductLikeCountProjectionRepository
import com.loopers.projection.like.port.ProductMetricsUpdateStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductMetricsProjectionService(
    private val processedKafkaEventRepository: ProcessedKafkaEventRepository,
    private val productLikeCountProjectionRepository: ProductLikeCountProjectionRepository,
) {
    @Transactional
    fun project(command: ProductMetricsProjectionCommand): ProductMetricsProjectionResult {
        require(command.deltas.isNotEmpty()) { "metrics delta is required." }
        val recorded = processedKafkaEventRepository.recordIfAbsent(
            eventId = command.eventId,
            consumerGroup = command.consumerGroup,
            eventType = command.eventType,
        )
        if (!recorded) {
            return ProductMetricsProjectionResult.duplicate()
        }

        val statuses = command.deltas.map { delta ->
            productLikeCountProjectionRepository.applyDelta(
                productId = delta.productId,
                likeDelta = delta.likeDelta,
                salesDelta = delta.salesDelta,
                viewDelta = delta.viewDelta,
                occurredAt = command.occurredAt,
            )
        }
        if (statuses.any { it == ProductMetricsUpdateStatus.MISSING || it == ProductMetricsUpdateStatus.INVALID }) {
            throw ProductMetricsProjectionException("상품 metrics 집계 행을 갱신할 수 없습니다.")
        }

        return if (statuses.all { it == ProductMetricsUpdateStatus.STALE }) {
            ProductMetricsProjectionResult.stale()
        } else {
            ProductMetricsProjectionResult.applied()
        }
    }
}
