package com.loopers.interfaces.event.like

import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.like.event.ProductLikeEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeEventListener(
    private val productStatService: ProductStatService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductLikeEvent.Like) {
        productStatService.increaseLikeCount(productId = event.productId, brandId = event.brandId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductLikeEvent.Unlike) {
        productStatService.decreaseLikeCount(productId = event.productId, brandId = event.brandId)
    }
}
