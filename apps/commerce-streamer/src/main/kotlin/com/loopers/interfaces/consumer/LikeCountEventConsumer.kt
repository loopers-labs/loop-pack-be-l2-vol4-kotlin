package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.config.kafka.KafkaConfig
import com.loopers.projection.like.application.ProductMetricsDelta
import com.loopers.projection.like.application.ProductMetricsProjectionCommand
import com.loopers.projection.like.application.ProductMetricsProjectionService
import java.time.ZonedDateTime
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class LikeCountEventConsumer(
    private val productMetricsProjectionService: ProductMetricsProjectionService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [
            "\${commerce-events.product-metrics.catalog-topic-name}",
            "\${commerce-events.product-metrics.order-topic-name}",
        ],
        groupId = CONSUMER_GROUP,
        containerFactory = KafkaConfig.RECORD_LISTENER,
    )
    fun consume(
        event: ProductMetricsKafkaEvent,
        acknowledgment: Acknowledgment,
    ) {
        event.toCommandOrNull()?.let { productMetricsProjectionService.project(it) }
        acknowledgment.acknowledge()
    }

    private fun ProductMetricsKafkaEvent.toCommandOrNull(): ProductMetricsProjectionCommand? {
        val occurredAt = createdAt?.let { ZonedDateTime.parse(it) } ?: ZonedDateTime.now()
        val deltas = when (eventType) {
            LIKE_COUNT_CHANGED_EVENT_TYPE -> listOf(likeCountDelta())

            PRODUCT_VIEWED_EVENT_TYPE -> listOf(
                ProductMetricsDelta(
                    productId = productViewedPayload().productId ?: requireNotNull(aggregateId) {
                        "productId is required."
                    },
                    viewDelta = 1,
                ),
            )

            ORDER_PAID_EVENT_TYPE -> orderPaidPayload().items.map {
                require(it.quantity > 0) { "quantity must be positive." }
                ProductMetricsDelta(
                    productId = it.productId,
                    salesDelta = it.quantity,
                )
            }

            else -> return null
        }
        return ProductMetricsProjectionCommand(
            eventId = eventId,
            consumerGroup = CONSUMER_GROUP,
            eventType = eventType,
            occurredAt = occurredAt,
            deltas = deltas,
        )
    }

    private fun ProductMetricsKafkaEvent.productViewedPayload(): ProductViewedPayload =
        payload?.let { objectMapper.readValue(it) } ?: ProductViewedPayload(productId = productId)

    private fun ProductMetricsKafkaEvent.likeCountDelta(): ProductMetricsDelta {
        val parsed = payload?.let { objectMapper.readValue<LikeCountChangedPayload>(it) } ?: LikeCountChangedPayload(
            productId = requireNotNull(productId) { "productId is required." },
            userId = requireNotNull(userId) { "userId is required." },
            delta = requireNotNull(delta) { "delta is required." },
        )
        requireNotNull(parsed.userId) { "userId is required." }
        val likeDelta = requireNotNull(parsed.delta) { "delta is required." }
        require(likeDelta == -1 || likeDelta == 1) { "delta must be -1 or 1." }
        return ProductMetricsDelta(
            productId = requireNotNull(parsed.productId) { "productId is required." },
            likeDelta = likeDelta,
        )
    }

    private fun ProductMetricsKafkaEvent.orderPaidPayload(): OrderPaidPayload =
        payload?.let { objectMapper.readValue(it) } ?: OrderPaidPayload(items = emptyList())

    companion object {
        const val CONSUMER_GROUP = "commerce-streamer-product-metrics"
        const val LIKE_COUNT_CHANGED_EVENT_TYPE = "LIKE_COUNT_CHANGED_V1"
        const val PRODUCT_VIEWED_EVENT_TYPE = "PRODUCT_VIEWED_V1"
        const val ORDER_PAID_EVENT_TYPE = "ORDER_PAID_V1"
    }
}
