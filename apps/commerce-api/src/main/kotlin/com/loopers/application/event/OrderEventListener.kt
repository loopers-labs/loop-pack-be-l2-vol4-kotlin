package com.loopers.application.event

import com.loopers.domain.event.OrderCancelledEvent
import com.loopers.domain.event.OrderCompletedEvent
import com.loopers.domain.event.OrderCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventListener {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        log.info(
            "[이벤트] 주문 생성 (orderId={}, userId={}, totalPrice={}, itemCount={})",
            event.orderId,
            event.userId,
            event.totalPrice,
            event.items.size,
        )
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCompleted(event: OrderCompletedEvent) {
        log.info(
            "[이벤트] 결제 완료 (orderId={}, userId={}, itemCount={})",
            event.orderId,
            event.userId,
            event.items.size,
        )
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCancelled(event: OrderCancelledEvent) {
        log.info(
            "[이벤트] 주문 취소 (orderId={}, userId={}, reason={})",
            event.orderId,
            event.userId,
            event.reason,
        )
    }
}
