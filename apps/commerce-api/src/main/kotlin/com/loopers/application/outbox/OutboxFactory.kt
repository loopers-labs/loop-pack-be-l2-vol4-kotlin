package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.ProductMetricType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

@Component
class OutboxFactory(
    private val objectMapper: ObjectMapper,
    @Value("\${product-metric.topic}") private val productMetricTopic: String,
    @Value("\${coupon.issue.topic}") private val couponIssueTopic: String,
) {
    fun createProductMetricOutbox(productId: Long, type: ProductMetricType, delta: Long): Outbox {
        val eventId = UUID.randomUUID().toString()
        val occurredAt = ZonedDateTime.now()
        val payload = objectMapper.writeValueAsString(
            ProductMetricPayload(
                eventId = eventId,
                productId = productId,
                type = type.name,
                delta = delta,
                occurredAt = occurredAt,
            ),
        )
        return Outbox.create(eventId = eventId, topic = productMetricTopic, payload = payload, occurredAt = occurredAt)
    }

    fun createCouponIssueOutbox(request: CouponIssueRequest): Outbox {
        val eventId = UUID.randomUUID().toString()
        val occurredAt = ZonedDateTime.now()
        val payload = objectMapper.writeValueAsString(
            CouponIssuePayload(
                eventId = eventId,
                issueRequestId = request.id,
                userId = request.userId,
                couponTemplateId = request.couponTemplateId,
                idempotencyKey = request.idempotencyKey,
                occurredAt = occurredAt,
            ),
        )
        return Outbox.create(eventId = eventId, topic = couponIssueTopic, payload = payload, occurredAt = occurredAt)
    }
}
