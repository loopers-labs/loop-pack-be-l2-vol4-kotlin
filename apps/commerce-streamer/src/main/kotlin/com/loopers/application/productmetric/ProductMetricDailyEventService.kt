package com.loopers.application.productmetric

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.productmetric.ProductMetricDailyRepository
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.NonRetryableEventException
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Component
class ProductMetricDailyEventService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricDailyRepository: ProductMetricDailyRepository,
    @Value("\${commerce.product-metric-daily.consumer-group:commerce-product-metric-daily}")
    private val consumerGroup: String = "commerce-product-metric-daily",
) {
    @Transactional
    fun handle(message: CatalogEventMessage) {
        validateCommon(message.eventId, message.productId)
        if (eventHandledRepository.exists(consumerGroup, message.eventId)) {
            return
        }

        productMetricDailyRepository.increment(
            metricDate = metricDateOf(message),
            productId = message.productId,
            viewCountDelta = if (message.eventType == CatalogEventType.PRODUCT_VIEWED) 1L else 0L,
            likeCountDelta = when (message.eventType) {
                CatalogEventType.PRODUCT_LIKED -> 1L
                CatalogEventType.PRODUCT_UNLIKED -> -1L
                CatalogEventType.PRODUCT_VIEWED -> 0L
            },
            salesAmountDelta = 0L,
        )
        recordHandled(message.eventId, message.eventType.name)
    }

    @Transactional
    fun handle(message: OrderEventMessage) {
        validateEventId(message.eventId)
        if (eventHandledRepository.exists(consumerGroup, message.eventId)) {
            return
        }

        if (message.eventType == OrderEventType.PAYMENT_SUCCEEDED) {
            val metricDate = metricDateOf(message)
            message.items
                .groupingBy { item ->
                    validateOrderItem(item.productId, item.quantity, item.unitPrice)
                    item.productId
                }
                .fold(0L) { totalAmount, item ->
                    addExact(
                        totalAmount,
                        multiplyExact(item.unitPrice, item.quantity),
                    )
                }
                .forEach { (productId, salesAmount) ->
                    productMetricDailyRepository.increment(
                        metricDate = metricDate,
                        productId = productId,
                        viewCountDelta = 0L,
                        likeCountDelta = 0L,
                        salesAmountDelta = salesAmount,
                    )
                }
        }

        recordHandled(message.eventId, message.eventType.name)
    }

    private fun metricDateOf(message: CatalogEventMessage): LocalDate {
        return message.occurredAt.withZoneSameInstant(KST).toLocalDate()
    }

    private fun metricDateOf(message: OrderEventMessage): LocalDate {
        return message.occurredAt.withZoneSameInstant(KST).toLocalDate()
    }

    private fun validateCommon(
        eventId: String,
        productId: Long,
    ) {
        validateEventId(eventId)
        if (productId <= 0L) {
            throw NonRetryableEventException("Product metric productId must be positive.")
        }
    }

    private fun validateEventId(eventId: String) {
        if (eventId.isBlank()) {
            throw NonRetryableEventException("Product metric eventId must not be blank.")
        }
    }

    private fun validateOrderItem(
        productId: Long,
        quantity: Long,
        unitPrice: Long,
    ) {
        if (productId <= 0L) {
            throw NonRetryableEventException("Product metric productId must be positive.")
        }
        if (quantity <= 0L) {
            throw NonRetryableEventException("Product metric quantity must be positive.")
        }
        if (unitPrice < 0L) {
            throw NonRetryableEventException("Product metric unitPrice must not be negative.")
        }
    }

    private fun multiplyExact(
        left: Long,
        right: Long,
    ): Long {
        return try {
            Math.multiplyExact(left, right)
        } catch (exception: ArithmeticException) {
            throw NonRetryableEventException("Product metric sales amount overflow.", exception)
        }
    }

    private fun addExact(
        left: Long,
        right: Long,
    ): Long {
        return try {
            Math.addExact(left, right)
        } catch (exception: ArithmeticException) {
            throw NonRetryableEventException("Product metric sales amount overflow.", exception)
        }
    }

    private fun recordHandled(
        eventId: String,
        eventType: String,
    ) {
        eventHandledRepository.save(
            EventHandled(
                consumerGroup = consumerGroup,
                eventId = eventId,
                eventType = eventType,
            ),
        )
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
