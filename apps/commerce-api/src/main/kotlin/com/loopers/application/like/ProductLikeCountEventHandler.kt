package com.loopers.application.like

import com.loopers.application.product.ProductCacheRepository
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.product.ProductRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeCountEventHandler(
    private val productRepository: ProductRepository,
    private val productCacheRepository: ProductCacheRepository,
) {
    // AFTER_COMMIT 시점에는 원본 트랜잭션이 이미 끝났으므로 REQUIRES_NEW로 새 트랜잭션을 연다.
    // (REQUIRED면 완료된 트랜잭션에 참여해 변경이 조용히 유실된다)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeCreatedEvent) {
        productRepository.incrementLikeCount(event.productId)
        productCacheRepository.evictDetail(event.productId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeDeletedEvent) {
        productRepository.decrementLikeCount(event.productId)
        productCacheRepository.evictDetail(event.productId)
    }
}
