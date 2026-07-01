package com.loopers.application.like

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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeChangedEvent) {
        try {
            if (event.activated) {
                productLikeCountCommandRepository.increment(event.productId)
            } else {
                productLikeCountCommandRepository.decrement(event.productId)
            }
        } catch (e: Exception) {
            log.error("좋아요 집계 갱신 실패: productId={}, activated={}", event.productId, event.activated, e)
        }
    }
}
