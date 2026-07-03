package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

data class MetricCommand(val eventId: String, val deltas: List<MetricDelta>)

@Component
class MetricEventMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toCommand(json: String): MetricCommand? {
        val node = objectMapper.readTree(json)
        val eventId = node["eventId"]?.asText() ?: return null
        val deltas = when (node["type"]?.asText()) {
            "LIKE_ADDED" -> listOf(MetricDelta(productId = node["productId"].asLong(), like = 1))
            "LIKE_REMOVED" -> listOf(MetricDelta(productId = node["productId"].asLong(), like = -1))
            "PRODUCT_VIEWED" -> listOf(MetricDelta(productId = node["productId"].asLong(), view = 1))
            "PAYMENT_SUCCEEDED" -> node["items"].map {
                MetricDelta(productId = it["productId"].asLong(), sales = it["quantity"].asLong())
            }
            else -> return null
        }
        return MetricCommand(eventId, deltas)
    }
}
