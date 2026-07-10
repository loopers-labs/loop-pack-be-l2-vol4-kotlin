package com.loopers.application.like

import com.loopers.config.async.AsyncConfig
import com.loopers.projection.product.ProductLikeCountCommandRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeCountProjectionEventListener(
    private val productLikeCountCommandRepository: ProductLikeCountCommandRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.EVENT_LISTENER_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductLikeCountProjectionIncreasedEvent) {
        try {
            productLikeCountCommandRepository.increment(event.productId)
        } catch (e: Exception) {
            log.error("좋아요 내부 집계 증가 실패: productId={}", event.productId, e)
        }
    }

    @Async(AsyncConfig.EVENT_LISTENER_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductLikeCountProjectionDecreasedEvent) {
        try {
            productLikeCountCommandRepository.decrement(event.productId)
        } catch (e: Exception) {
            log.error("좋아요 내부 집계 감소 실패: productId={}", event.productId, e)
        }
    }
}
