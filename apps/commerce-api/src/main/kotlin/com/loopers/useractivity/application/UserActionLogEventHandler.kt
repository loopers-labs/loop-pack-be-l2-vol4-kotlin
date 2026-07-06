package com.loopers.useractivity.application

import com.loopers.order.domain.event.OrderCreatedEvent
import com.loopers.product.domain.event.ProductLikedEvent
import com.loopers.product.domain.event.ProductUnlikedEvent
import com.loopers.product.domain.event.ProductViewedEvent
import com.loopers.useractivity.domain.UserActionLog
import com.loopers.useractivity.domain.UserActionLogRepository
import com.loopers.useractivity.domain.UserActionType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 도메인 사실(주문·조회·좋아요)을 구독해 유저 행동 로그를 append 한다. 커밋 후 비동기.
 * 적재 실패는 로깅만 — 행동 로그는 유실 허용이라 본 트랜잭션과 독립.
 */
@Component
class UserActionLogEventHandler(
    private val userActionLogRepository: UserActionLogRepository,
) {
    private val logger = LoggerFactory.getLogger(UserActionLogEventHandler::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderCreated(event: OrderCreatedEvent) = append(event.userId, UserActionType.ORDER, "ORDER", event.orderId)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductViewed(event: ProductViewedEvent) = append(event.userId, UserActionType.VIEW, "PRODUCT", event.productId)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLiked(event: ProductLikedEvent) = append(event.userId, UserActionType.LIKE, "PRODUCT", event.productId)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUnliked(event: ProductUnlikedEvent) = append(event.userId, UserActionType.UNLIKE, "PRODUCT", event.productId)

    private fun append(userId: Long?, actionType: UserActionType, targetType: String, targetId: Long) {
        try {
            userActionLogRepository.append(UserActionLog(userId, actionType, targetType, targetId))
        } catch (e: Exception) {
            logger.warn(
                "UserActionLog append 실패 (userId={}, actionType={}, targetType={}, targetId={}): {}",
                userId,
                actionType,
                targetType,
                targetId,
                e.javaClass.simpleName,
            )
        }
    }
}
