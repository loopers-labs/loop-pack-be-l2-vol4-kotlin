package com.loopers.like.application

import com.loopers.product.domain.event.ProductLikedEvent
import com.loopers.product.domain.event.ProductUnlikedEvent
import com.loopers.like.domain.LikeAction
import com.loopers.like.domain.LikeEvent
import com.loopers.like.domain.LikeEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Like 가 좋아요 사실을 구독해 이력(LikeEvent)을 append 한다. 커밋 후 비동기.
 * 적재 실패는 로깅만 — 본 토글 트랜잭션과 독립(L-?7).
 */
@Component
class LikeEventHandler(
    private val likeEventRepository: LikeEventRepository,
) {
    private val logger = LoggerFactory.getLogger(LikeEventHandler::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLiked(event: ProductLikedEvent) = append(event.userId, event.productId, LikeAction.LIKE)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUnliked(event: ProductUnlikedEvent) = append(event.userId, event.productId, LikeAction.UNLIKE)

    private fun append(userId: Long, productId: Long, action: LikeAction) {
        try {
            likeEventRepository.append(LikeEvent(userId, productId, action))
        } catch (e: Exception) {
            logger.warn(
                "LikeEvent append 실패 (userId={}, productId={}, action={}): {}",
                userId,
                productId,
                action,
                e.javaClass.simpleName,
            )
        }
    }
}
