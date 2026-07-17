package com.loopers.application.event

import com.loopers.config.kafka.Topics
import com.loopers.config.kafka.event.OrderEvent
import com.loopers.config.kafka.event.OrderEventItem
import com.loopers.domain.event.OrderCancelledEvent
import com.loopers.domain.event.OrderCompletedEvent
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.outbox.OutboxService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 관련 ApplicationEvent를 수신하여 Outbox에 기록하는 리스너.
 * BEFORE_COMMIT phase에서 동작하여 도메인 트랜잭션과 원자적으로 Outbox에 저장한다.
 */
@Component
class OrderEventListener(
    private val outboxService: OutboxService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 생성 이벤트를 Outbox에 기록한다.
     * 파티션 키: orderId (동일 주문의 이벤트 순서 보장)
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        log.info("[이벤트] 주문 생성 → Outbox 기록 (orderId={})", event.orderId)
        outboxService.save(
            topic = Topics.ORDER_EVENTS,
            partitionKey = event.orderId.toString(),
            payload = OrderEvent(
                eventId = "",
                eventType = "ORDER_CREATED",
                orderId = event.orderId,
                userId = event.userId,
                totalPrice = event.totalPrice,
                items = event.items.map {
                    OrderEventItem(
                        productId = it.productId,
                        productName = it.productName,
                        quantity = it.quantity,
                        price = it.price,
                    )
                },
            ),
        )
    }

    /**
     * 결제 완료 이벤트를 Outbox에 기록한다.
     * Consumer가 이 이벤트를 수신하면 product_metrics에 판매량을 반영한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrderCompleted(event: OrderCompletedEvent) {
        log.info("[이벤트] 결제 완료 → Outbox 기록 (orderId={})", event.orderId)
        outboxService.save(
            topic = Topics.ORDER_EVENTS,
            partitionKey = event.orderId.toString(),
            payload = OrderEvent(
                eventId = "",
                eventType = "ORDER_COMPLETED",
                orderId = event.orderId,
                userId = event.userId,
                items = event.items.map {
                    OrderEventItem(
                        productId = it.productId,
                        productName = it.productName,
                        quantity = it.quantity,
                        price = it.price,
                    )
                },
            ),
        )
    }

    /**
     * 주문 취소 이벤트를 Outbox에 기록한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrderCancelled(event: OrderCancelledEvent) {
        log.info("[이벤트] 주문 취소 → Outbox 기록 (orderId={})", event.orderId)
        outboxService.save(
            topic = Topics.ORDER_EVENTS,
            partitionKey = event.orderId.toString(),
            payload = OrderEvent(
                eventId = "",
                eventType = "ORDER_CANCELLED",
                orderId = event.orderId,
                userId = event.userId,
                reason = event.reason,
            ),
        )
    }
}
