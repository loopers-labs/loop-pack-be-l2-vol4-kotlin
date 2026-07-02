package com.loopers.projection.like.application

import com.loopers.projection.like.port.ProcessedKafkaEventRepository
import com.loopers.projection.like.port.ProductLikeCountProjectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeCountProjectionService(
    private val processedKafkaEventRepository: ProcessedKafkaEventRepository,
    private val productLikeCountProjectionRepository: ProductLikeCountProjectionRepository,
) {
    @Transactional
    fun project(command: LikeCountProjectionCommand): LikeCountProjectionResult {
        val recorded = processedKafkaEventRepository.recordIfAbsent(
            eventId = command.eventId,
            consumerGroup = command.consumerGroup,
            eventType = command.eventType,
        )
        if (!recorded) {
            return LikeCountProjectionResult.duplicate()
        }

        val updatedRows = when (command.delta) {
            1 -> productLikeCountProjectionRepository.increment(command.productId)
            -1 -> productLikeCountProjectionRepository.decrement(command.productId)
            else -> throw LikeCountProjectionException("지원하지 않는 좋아요 수 delta 입니다: ${command.delta}")
        }
        if (updatedRows != 1) {
            throw LikeCountProjectionException("상품 좋아요 수 집계 행을 갱신할 수 없습니다.")
        }

        return LikeCountProjectionResult.applied()
    }
}
