package com.loopers.application.like

import com.loopers.application.product.ProductCacheStore
import com.loopers.domain.like.ProductLikedEvent
import com.loopers.domain.like.ProductUnlikedEvent
import com.loopers.domain.product.ProductService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventListener(
    private val productService: ProductService,
    private val productCacheStore: ProductCacheStore,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLiked(event: ProductLikedEvent) {
        productService.increaseLikeCount(event.productId)
        productCacheStore.evictProductDetail(event.productId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUnliked(event: ProductUnlikedEvent) {
        productService.decreaseLikeCount(event.productId)
        productCacheStore.evictProductDetail(event.productId)
    }
}
