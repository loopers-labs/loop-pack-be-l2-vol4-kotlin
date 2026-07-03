package com.loopers.application.useraction

import com.loopers.domain.like.ProductLikedEvent
import com.loopers.domain.like.ProductUnlikedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.product.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 유저 행동(시도)의 서버 레벨 로깅.
 * "시도"는 트랜잭션 결과와 무관한 사실이므로 plain @EventListener + @Async 로 즉시 기록한다.
 */
@Component
class UserActionLogListener {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun onProductViewed(event: ProductViewedEvent) {
        log.info("USER_ACTION action=PRODUCT_VIEWED productId={}", event.productId)
    }

    @Async
    @EventListener
    fun onProductLiked(event: ProductLikedEvent) {
        log.info("USER_ACTION action=PRODUCT_LIKED userId={} productId={}", event.userId, event.productId)
    }

    @Async
    @EventListener
    fun onProductUnliked(event: ProductUnlikedEvent) {
        log.info("USER_ACTION action=PRODUCT_UNLIKED userId={} productId={}", event.userId, event.productId)
    }

    @Async
    @EventListener
    fun onOrderCreated(event: OrderCreatedEvent) {
        log.info(
            "USER_ACTION action=ORDER_CREATED userId={} orderId={} finalAmount={}",
            event.userId,
            event.orderId,
            event.finalAmount,
        )
    }
}
