package com.loopers.support.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.order.application.service.OrderService
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxAggregateType
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CommerceApplicationEventOutboxListener(
    private val outboxRepository: OutboxRepository,
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onProductViewed(event: ProductViewedApplicationEvent) {
        log.info("committed product viewed: productId={}", event.productId)
        saveOutbox(
            type = CommerceOutboxEventType.PRODUCT_VIEWED_V1,
            aggregateType = CommerceOutboxAggregateType.PRODUCT,
            aggregateId = event.productId,
            payload = objectMapper.writeValueAsString(ProductViewedPayload(event.productId)),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onOrderCreated(event: OrderCreatedApplicationEvent) {
        log.info("committed order created: orderId={}", event.orderId)
        saveOutbox(
            type = CommerceOutboxEventType.ORDER_CREATED_V1,
            aggregateType = CommerceOutboxAggregateType.ORDER,
            aggregateId = event.orderId,
            payload = objectMapper.writeValueAsString(OrderMetricsPayload(event.orderId, event.items)),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPaymentApproved(event: PaymentApprovedApplicationEvent) {
        val order = findOrderOrNull(event.orderId) ?: return
        val items = order.items.map {
            CommerceEventOrderItem(productId = it.productId, quantity = it.quantity.value)
        }
        log.info("committed payment approved: paymentId={}, orderId={}", event.paymentId, event.orderId)
        saveOutbox(
            type = CommerceOutboxEventType.ORDER_PAID_V1,
            aggregateType = CommerceOutboxAggregateType.ORDER,
            aggregateId = event.orderId,
            payload = objectMapper.writeValueAsString(OrderMetricsPayload(event.orderId, items)),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPaymentFailed(event: PaymentFailedApplicationEvent) {
        findOrderOrNull(event.orderId) ?: return
        log.info("committed payment failed: paymentId={}, orderId={}", event.paymentId, event.orderId)
        saveOutbox(
            type = CommerceOutboxEventType.ORDER_FAILED_V1,
            aggregateType = CommerceOutboxAggregateType.ORDER,
            aggregateId = event.orderId,
            payload = objectMapper.writeValueAsString(PaymentOrderPayload(event.orderId, event.paymentId)),
        )
    }

    private fun findOrderOrNull(orderId: Long) =
        try {
            orderService.getById(orderId)
        } catch (e: CoreException) {
            if (e.errorType == ErrorType.NOT_FOUND) null else throw e
        }

    private fun saveOutbox(
        type: CommerceOutboxEventType,
        aggregateType: CommerceOutboxAggregateType,
        aggregateId: Long,
        payload: String,
    ) {
        outboxRepository.save(
            OutboxEventModel(
                type = type.name,
                aggregateType = aggregateType.value,
                aggregateId = aggregateId,
                payload = payload,
            ),
        )
    }
}
