package com.loopers.application.ranking

import com.loopers.config.redis.RankingClockConfig
import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.ranking.CatalogRankingMetric
import com.loopers.domain.ranking.CatalogRankingProjection
import com.loopers.domain.ranking.OrderRankingProjection
import com.loopers.domain.ranking.RankingProjectionRepository
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.NonRetryableEventException
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class RankingEventService(
    private val repository: RankingProjectionRepository,
    properties: RankingRedisProperties,
    @param:Qualifier(RankingClockConfig.RANKING_CLOCK)
    private val clock: Clock,
) {
    private val datePolicy = RankingDatePolicy(properties)

    fun projectCatalog(message: CatalogEventMessage) {
        validateCommon(message.eventId, message.productId)
        val (metric, delta) = when (message.eventType) {
            CatalogEventType.PRODUCT_VIEWED -> CatalogRankingMetric.VIEW to 1L
            CatalogEventType.PRODUCT_LIKED -> CatalogRankingMetric.LIKE to 1L
            CatalogEventType.PRODUCT_UNLIKED -> CatalogRankingMetric.LIKE to -1L
        }
        val date = datePolicy.dateOf(message.occurredAt)
        if (datePolicy.isExpired(date, clock.instant())) {
            return
        }

        repository.projectCatalog(
            CatalogRankingProjection(
                eventId = message.eventId,
                productId = message.productId,
                date = date,
                metric = metric,
                delta = delta,
                expiresAt = datePolicy.expiresAt(date),
            ),
        )
    }

    fun projectOrder(message: OrderEventMessage) {
        if (message.eventType != OrderEventType.PAYMENT_SUCCEEDED) {
            return
        }
        if (message.eventId.isBlank()) {
            throw NonRetryableEventException("Ranking eventId must not be blank.")
        }
        if (message.items.isEmpty()) {
            throw NonRetryableEventException("Payment succeeded event items must not be empty.")
        }
        val salesByProduct = linkedMapOf<Long, Long>()
        message.items.forEach { item ->
            validateSalesItem(item.productId, item.quantity, item.unitPrice)
            val amount = exactAmount(item.unitPrice, item.quantity)
            salesByProduct[item.productId] = exactSum(salesByProduct[item.productId] ?: 0L, amount)
        }
        val date = datePolicy.dateOf(message.occurredAt)
        if (datePolicy.isExpired(date, clock.instant())) {
            return
        }

        repository.projectOrder(
            OrderRankingProjection(
                eventId = message.eventId,
                date = date,
                items = salesByProduct.map { (productId, amount) ->
                    OrderRankingProjection.SalesItem(productId, amount)
                },
                expiresAt = datePolicy.expiresAt(date),
            ),
        )
    }

    private fun validateCommon(eventId: String, productId: Long) {
        if (eventId.isBlank()) {
            throw NonRetryableEventException("Ranking eventId must not be blank.")
        }
        validateProductId(productId)
    }

    private fun validateProductId(productId: Long) {
        if (productId <= 0L) {
            throw NonRetryableEventException("Ranking productId must be positive.")
        }
    }

    private fun validateSalesItem(productId: Long, quantity: Long, unitPrice: Long) {
        validateProductId(productId)
        if (quantity <= 0L) {
            throw NonRetryableEventException("Ranking sales quantity must be positive.")
        }
        if (unitPrice < 0L) {
            throw NonRetryableEventException("Ranking sales unitPrice must not be negative.")
        }
    }

    private fun exactAmount(unitPrice: Long, quantity: Long): Long {
        return try {
            Math.multiplyExact(unitPrice, quantity)
        } catch (exception: ArithmeticException) {
            throw NonRetryableEventException("Ranking sales amount overflowed.", exception)
        }
    }

    private fun exactSum(current: Long, amount: Long): Long {
        return try {
            Math.addExact(current, amount)
        } catch (exception: ArithmeticException) {
            throw NonRetryableEventException("Ranking accumulated sales amount overflowed.", exception)
        }
    }
}
