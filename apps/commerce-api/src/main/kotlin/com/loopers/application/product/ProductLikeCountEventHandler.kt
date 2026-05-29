package com.loopers.application.product

import com.loopers.domain.event.ProductLikedEvent
import com.loopers.domain.event.ProductUnlikedEvent
import com.loopers.domain.product.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Product 가 좋아요 사실을 구독해 자기 like_count(비정규화)를 갱신한다. 커밋 후 비동기 + 원자 증감.
 * 갱신 실패는 로깅만 — 결과적 일관성(틀어지면 like_event/COUNT 로 재집계).
 */
@Component
class ProductLikeCountEventHandler(
    private val productRepository: ProductRepository,
) {
    private val logger = LoggerFactory.getLogger(ProductLikeCountEventHandler::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLiked(event: ProductLikedEvent) {
        try {
            productRepository.increaseLikeCount(event.productId)
        } catch (e: Exception) {
            logger.warn("like_count 증가 실패 (productId={}): {}", event.productId, e.javaClass.simpleName)
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUnliked(event: ProductUnlikedEvent) {
        try {
            productRepository.decreaseLikeCount(event.productId)
        } catch (e: Exception) {
            logger.warn("like_count 감소 실패 (productId={}): {}", event.productId, e.javaClass.simpleName)
        }
    }
}
