package com.loopers.support.event

import com.loopers.support.outbox.OutboxEventStatus
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.support.outbox.persistence.OutboxEventJpaRepository
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

class OrderCreatedRollbackProbe(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) {
    var observedOutboxCount: Int = 0
        private set
    private var enabled: Boolean = false

    fun enable() {
        enabled = true
        observedOutboxCount = 0
    }

    fun disable() {
        enabled = false
        observedOutboxCount = 0
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onOrderCreated(event: OrderCreatedApplicationEvent) {
        if (!enabled) return
        observedOutboxCount = outboxEventJpaRepository.findAllByTypeAndStatus(
            CommerceOutboxEventType.ORDER_CREATED_V1.name,
            OutboxEventStatus.PENDING,
        ).size
        throw IllegalStateException("forced rollback after outbox observation")
    }
}
