package com.loopers.application.order

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.product.ProductStat
import com.loopers.domain.product.ProductStatRepository
import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionLogRepository
import com.loopers.domain.useraction.UserActionType
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderEventService(
    private val eventHandledRepository: EventHandledRepository,
    private val productStatRepository: ProductStatRepository,
    private val userActionLogRepository: UserActionLogRepository,
) {
    @Transactional
    fun handle(message: OrderEventMessage) {
        if (eventHandledRepository.exists(message.eventId)) {
            return
        }

        updateProductStat(message)
        userActionLogRepository.save(
            UserActionLog(
                eventId = message.eventId,
                actionType = message.eventType.toUserActionType(),
                memberId = message.memberId,
                aggregateId = message.aggregateId,
                productId = null,
                occurredAt = message.occurredAt,
            ),
        )
        eventHandledRepository.save(
            EventHandled(
                eventId = message.eventId,
                eventType = message.eventType.name,
            ),
        )
    }

    private fun updateProductStat(message: OrderEventMessage) {
        if (message.eventType != OrderEventType.PAYMENT_SUCCEEDED) {
            return
        }

        message.items.forEach { item ->
            val current = productStatRepository.findByProductIdForUpdate(item.productId)
            val productStat = current ?: ProductStat(
                productId = item.productId,
                brandId = 0L,
                likeCount = 0L,
                salesCount = 0L,
                viewCount = 0L,
                latestEventVersion = 0L,
            )

            productStat.salesCount += item.quantity
            productStatRepository.save(productStat)
        }
    }

    private fun OrderEventType.toUserActionType(): UserActionType {
        return when (this) {
            OrderEventType.ORDER_CREATED -> UserActionType.ORDER_CREATED
            OrderEventType.PAYMENT_REQUESTED -> UserActionType.PAYMENT_REQUESTED
            OrderEventType.PAYMENT_SUCCEEDED -> UserActionType.PAYMENT_SUCCEEDED
            OrderEventType.PAYMENT_FAILED -> UserActionType.PAYMENT_FAILED
        }
    }
}
