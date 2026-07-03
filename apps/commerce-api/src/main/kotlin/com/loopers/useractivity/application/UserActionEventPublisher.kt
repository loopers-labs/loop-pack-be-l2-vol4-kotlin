package com.loopers.useractivity.application

import com.loopers.outbox.domain.EventMessagePublisher
import com.loopers.outbox.domain.EventTopics
import com.loopers.product.domain.event.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionEventPublisher(
    private val eventMessagePublisher: EventMessagePublisher,
) {
    private val logger = LoggerFactory.getLogger(UserActionEventPublisher::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductViewed(event: ProductViewedEvent) {
        try {
            eventMessagePublisher.publish(EventTopics.USER_ACTION_EVENTS, event.productId.toString(), event)
        } catch (e: Exception) {
            logger.warn("ProductViewedEvent 발행 실패 — 유실 허용 (productId={}): {}", event.productId, e.javaClass.simpleName)
        }
    }
}
