package com.loopers.application.event

import com.loopers.domain.event.ProductLikedEvent
import com.loopers.domain.event.ProductUnlikedEvent
import com.loopers.domain.event.ProductViewedEvent
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.product.ProductCacheManager
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductEventListener(
    private val productService: ProductService,
    private val productCacheManager: ProductCacheManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("eventExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductLiked(event: ProductLikedEvent) {
        log.info("[이벤트] 좋아요 (userId={}, productId={})", event.userId, event.productId)
        productService.incrementLikeCount(event.productId)
        productCacheManager.evictDetail(event.productId)
    }

    @Async("eventExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductUnliked(event: ProductUnlikedEvent) {
        log.info("[이벤트] 좋아요 취소 (userId={}, productId={})", event.userId, event.productId)
        productService.decrementLikeCount(event.productId)
        productCacheManager.evictDetail(event.productId)
    }

    @Async("eventExecutor")
    @EventListener
    fun handleProductViewed(event: ProductViewedEvent) {
        log.info("[이벤트] 상품 조회 (userId={}, productId={})", event.userId, event.productId)
    }
}
