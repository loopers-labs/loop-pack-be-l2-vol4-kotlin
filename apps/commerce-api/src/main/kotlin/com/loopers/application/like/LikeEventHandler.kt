package com.loopers.application.like

import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventHandler(
    private val likeEventRepository: LikeEventRepository,
) {
    private val logger = LoggerFactory.getLogger(LikeEventHandler::class.java)

    // 커밋 후 별도 트랜잭션에서 비동기 append. 적재 실패는 로깅만 — 본 토글 트랜잭션을 막지 않는다(L-?7).
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun append(event: LikeChangedEvent) {
        try {
            likeEventRepository.append(LikeEvent(event.userId, event.productId, event.action))
        } catch (e: Exception) {
            logger.warn(
                "LikeEvent append 실패 (userId={}, productId={}, action={}): {}",
                event.userId,
                event.productId,
                event.action,
                e.javaClass.simpleName,
            )
        }
    }
}
