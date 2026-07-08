package com.loopers.application.audit

import com.loopers.application.like.LikeEvent
import com.loopers.application.order.OrderCreatedEvent
import com.loopers.application.payment.PaymentCompletedEvent
import com.loopers.domain.audit.AuditAction
import com.loopers.domain.audit.AuditService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AuditEventListener(
    private val auditService: AuditService,
) {
    private val log: Logger = LogManager.getLogger(AuditEventListener::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLike(event: LikeEvent) {
        val action = when (event.action) {
            LikeEvent.LikeAction.LIKED -> AuditAction.LIKE
            LikeEvent.LikeAction.UNLIKED -> AuditAction.UNLIKE
        }
        log.info("[AUDIT] {} userId={} productId={}", action, event.userId, event.productId)
        auditService.record(
            action = action,
            actorId = event.userId,
            targetId = event.productId,
            description = "${event.action.name.lowercase()} productId=${event.productId}",
        )
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        log.info("[AUDIT] ORDER_CREATED userId={} orderId={}", event.userId, event.orderId)
        auditService.record(
            action = AuditAction.ORDER_CREATED,
            actorId = event.userId,
            targetId = event.orderId,
            description = "order created orderId=${event.orderId}",
        )
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        log.info(
            "[AUDIT] PAYMENT_COMPLETED userId={} orderId={} paymentId={}",
            event.userId,
            event.orderId,
            event.paymentId,
        )
        auditService.record(
            action = AuditAction.PAYMENT_COMPLETED,
            actorId = event.userId,
            targetId = event.paymentId,
            description = "payment completed paymentId=${event.paymentId} orderId=${event.orderId}",
        )
    }
}
