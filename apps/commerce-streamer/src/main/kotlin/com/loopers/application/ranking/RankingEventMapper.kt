package com.loopers.application.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.RankingScoreDelta
import com.loopers.domain.ranking.RankingScoreEntry
import com.loopers.domain.ranking.RankingScorePolicy
import org.springframework.stereotype.Component

@Component
class RankingEventMapper(
    private val objectMapper: ObjectMapper,
    private val scorePolicy: RankingScorePolicy,
) {
    fun toEntry(json: String): RankingScoreEntry? {
        val node = objectMapper.readTree(json)
        val eventId = node["eventId"]?.asText() ?: return null
        val deltas = when (node["type"]?.asText()) {
            "PRODUCT_VIEWED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(RankingScoreDelta(productId, scorePolicy.viewed()))
            }
            "LIKE_ADDED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(RankingScoreDelta(productId, scorePolicy.likeAdded()))
            }
            "LIKE_REMOVED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(RankingScoreDelta(productId, scorePolicy.likeRemoved()))
            }
            "PAYMENT_SUCCEEDED" -> {
                val items = node["items"] ?: return null
                items.mapNotNull {
                    val productId = it["productId"]?.asLong() ?: return@mapNotNull null
                    val quantity = it["quantity"]?.asInt() ?: return@mapNotNull null
                    val unitPrice = it["unitPrice"]?.decimalValue() ?: return@mapNotNull null
                    RankingScoreDelta(productId, scorePolicy.ordered(unitPrice, quantity))
                }
            }
            else -> return null
        }
        return RankingScoreEntry(eventId, deltas)
    }
}
