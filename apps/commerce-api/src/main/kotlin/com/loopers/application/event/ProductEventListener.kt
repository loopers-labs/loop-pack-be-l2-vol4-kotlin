package com.loopers.application.event

import com.loopers.config.kafka.Topics
import com.loopers.config.kafka.event.CatalogEvent
import com.loopers.domain.event.ProductLikedEvent
import com.loopers.domain.event.ProductUnlikedEvent
import com.loopers.domain.event.ProductViewedEvent
import com.loopers.domain.outbox.OutboxService
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.product.ProductCacheManager
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 상품 관련 ApplicationEvent를 수신하는 리스너.
 * 좋아요/좋아요취소: likeCount 갱신 + Outbox 기록 (eventual consistency)
 * 조회: Outbox 기록 (집계용)
 */
@Component
class ProductEventListener(
    private val productService: ProductService,
    private val productCacheManager: ProductCacheManager,
    private val outboxService: OutboxService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 좋아요 이벤트 처리.
     * 1. 상품 likeCount 증가 (새 트랜잭션)
     * 2. 캐시 무효화
     * 3. Outbox에 PRODUCT_LIKED 이벤트 기록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductLiked(event: ProductLikedEvent) {
        log.info("[이벤트] 좋아요 (userId={}, productId={})", event.userId, event.productId)
        productService.incrementLikeCount(event.productId)
        productCacheManager.evictDetail(event.productId)

        outboxService.save(
            topic = Topics.CATALOG_EVENTS,
            partitionKey = event.productId.toString(),
            payload = CatalogEvent(
                eventId = "",
                eventType = "PRODUCT_LIKED",
                productId = event.productId,
                userId = event.userId,
            ),
        )
    }

    /**
     * 좋아요 취소 이벤트 처리.
     * 1. 상품 likeCount 감소 (새 트랜잭션)
     * 2. 캐시 무효화
     * 3. Outbox에 PRODUCT_UNLIKED 이벤트 기록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductUnliked(event: ProductUnlikedEvent) {
        log.info("[이벤트] 좋아요 취소 (userId={}, productId={})", event.userId, event.productId)
        productService.decrementLikeCount(event.productId)
        productCacheManager.evictDetail(event.productId)

        outboxService.save(
            topic = Topics.CATALOG_EVENTS,
            partitionKey = event.productId.toString(),
            payload = CatalogEvent(
                eventId = "",
                eventType = "PRODUCT_UNLIKED",
                productId = event.productId,
                userId = event.userId,
            ),
        )
    }

    /**
     * 상품 조회 이벤트 처리.
     * 비트랜잭셔널 이벤트 — 조회수 집계를 위해 Outbox에 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @EventListener
    fun handleProductViewed(event: ProductViewedEvent) {
        log.info("[이벤트] 상품 조회 (userId={}, productId={})", event.userId, event.productId)
        outboxService.save(
            topic = Topics.CATALOG_EVENTS,
            partitionKey = event.productId.toString(),
            payload = CatalogEvent(
                eventId = "",
                eventType = "PRODUCT_VIEWED",
                productId = event.productId,
                userId = event.userId,
            ),
        )
    }
}
