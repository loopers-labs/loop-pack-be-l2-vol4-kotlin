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
            "LIKE_ADDED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(MetricDelta(productId = productId, like = 1))
            }
            "LIKE_REMOVED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(MetricDelta(productId = productId, like = -1))
            }
            "PRODUCT_VIEWED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(MetricDelta(productId = productId, view = 1))
            }
            "PAYMENT_SUCCEEDED" -> {
                val items = node["items"] ?: return null
                items.mapNotNull {
                    val productId = it["productId"]?.asLong() ?: return@mapNotNull null
                    val quantity = it["quantity"]?.asLong() ?: return@mapNotNull null
                    MetricDelta(productId = productId, sales = quantity)
                }
            }
            else -> return null
        }
        return MetricCommand(eventId, deltas)
    }
}
