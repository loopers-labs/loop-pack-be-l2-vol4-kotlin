package com.loopers.support.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.CouponIssueTopicProperties
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxAggregateType
import com.loopers.support.outbox.event.CommerceOutboxEventType
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CommerceApplicationEventOutboxListener(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val couponIssueTopicProperties: CouponIssueTopicProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onLikeChanged(event: LikeChangedApplicationEvent) {
        saveOutbox(
            type = CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1,
            aggregateType = CommerceOutboxAggregateType.PRODUCT,
            aggregateId = event.productId,
            partitionKey = event.productId.toString(),
            payload = objectMapper.writeValueAsString(
                LikeChangedPayload(event.productId, event.userId, event.delta, event.occurredAt),
            ),
            occurredAt = event.occurredAt,
        )
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onCouponIssueRequested(event: CouponIssueRequestedApplicationEvent) {
        saveOutbox(
            type = CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1,
            aggregateType = CommerceOutboxAggregateType.COUPON_ISSUE_REQUEST,
            aggregateId = event.requestAggregateId,
            topicName = couponIssueTopicProperties.topicName,
            partitionKey = event.couponTemplateId.toString(),
            payload = objectMapper.writeValueAsString(
                CouponIssueRequestedPayload(event.requestId, event.userId, event.couponTemplateId),
            ),
            occurredAt = event.occurredAt,
        )
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onProductViewed(event: ProductViewedApplicationEvent) {
        saveOutbox(
            type = CommerceOutboxEventType.PRODUCT_VIEWED_V1,
            aggregateType = CommerceOutboxAggregateType.PRODUCT,
            aggregateId = event.productId,
            partitionKey = event.productId.toString(),
            payload = objectMapper.writeValueAsString(ProductViewedPayload(event.productId)),
            occurredAt = event.occurredAt,
        )
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onOrderCreated(event: OrderCreatedApplicationEvent) {
        saveOutbox(
            type = CommerceOutboxEventType.ORDER_CREATED_V1,
            aggregateType = CommerceOutboxAggregateType.ORDER,
            aggregateId = event.orderId,
            partitionKey = event.orderId.toString(),
            payload = objectMapper.writeValueAsString(OrderMetricsPayload(event.orderId, event.items)),
            occurredAt = event.occurredAt,
        )
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onPaymentApproved(event: PaymentApprovedApplicationEvent) {
        saveOutbox(
            type = CommerceOutboxEventType.ORDER_PAID_V1,
            aggregateType = CommerceOutboxAggregateType.ORDER,
            aggregateId = event.orderId,
            partitionKey = event.orderId.toString(),
            payload = objectMapper.writeValueAsString(OrderMetricsPayload(event.orderId, event.items)),
            occurredAt = event.occurredAt,
        )
    }

    @Order(0)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onPaymentFailed(event: PaymentFailedApplicationEvent) {
        saveOutbox(
            type = CommerceOutboxEventType.ORDER_FAILED_V1,
            aggregateType = CommerceOutboxAggregateType.ORDER,
            aggregateId = event.orderId,
            partitionKey = event.orderId.toString(),
            payload = objectMapper.writeValueAsString(PaymentOrderPayload(event.orderId, event.paymentId)),
            occurredAt = event.occurredAt,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logProductViewed(event: ProductViewedApplicationEvent) {
        log.info("committed product viewed: productId={}", event.productId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logOrderCreated(event: OrderCreatedApplicationEvent) {
        log.info("committed order created: orderId={}", event.orderId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logPaymentApproved(event: PaymentApprovedApplicationEvent) {
        log.info("committed payment approved: paymentId={}, orderId={}", event.paymentId, event.orderId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logPaymentFailed(event: PaymentFailedApplicationEvent) {
        log.info("committed payment failed: paymentId={}, orderId={}", event.paymentId, event.orderId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logLikeChanged(event: LikeChangedApplicationEvent) {
        log.info(
            "committed like changed: productId={}, userId={}, delta={}",
            event.productId,
            event.userId,
            event.delta,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logCouponIssueRequested(event: CouponIssueRequestedApplicationEvent) {
        log.info(
            "committed coupon issue requested: requestId={}, userId={}, couponTemplateId={}",
            event.requestId,
            event.userId,
            event.couponTemplateId,
        )
    }

    private fun saveOutbox(
        type: CommerceOutboxEventType,
        aggregateType: CommerceOutboxAggregateType,
        aggregateId: Long,
        topicName: String = requireNotNull(type.defaultTopicName),
        partitionKey: String,
        payload: String,
        occurredAt: java.time.ZonedDateTime,
    ) {
        outboxRepository.save(
            OutboxEventModel.publishable(
                type = type.name,
                aggregateType = aggregateType.value,
                aggregateId = aggregateId,
                topicName = topicName,
                partitionKey = partitionKey,
                payload = payload,
                createdAt = occurredAt,
            ),
        )
    }
}
