package com.loopers.application.catalog

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.product.ProductStat
import com.loopers.domain.product.ProductStatRepository
import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionLogRepository
import com.loopers.domain.useraction.UserActionType
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CatalogEventService(
    private val eventHandledRepository: EventHandledRepository,
    private val productStatRepository: ProductStatRepository,
    private val userActionLogRepository: UserActionLogRepository,
) {
    @Transactional
    fun project(message: CatalogEventMessage) {
        if (eventHandledRepository.exists(message.eventId)) {
            return
        }

        updateProductStat(message)
        recordUserAction(message)
        eventHandledRepository.save(
            EventHandled(
                eventId = message.eventId,
                eventType = message.eventType.name,
            ),
        )
    }

    private fun updateProductStat(message: CatalogEventMessage) {
        val current = productStatRepository.findByProductIdForUpdate(message.productId)

        if (message.eventType != CatalogEventType.PRODUCT_VIEWED &&
            current != null &&
            current.latestEventVersion > message.version
        ) {
            return
        }

        val productStat = current ?: ProductStat(
            productId = message.productId,
            brandId = message.brandId ?: 0L,
            likeCount = 0L,
            salesCount = 0L,
            viewCount = 0L,
            latestEventVersion = 0L,
        )

        when (message.eventType) {
            CatalogEventType.PRODUCT_LIKED -> productStat.likeCount += 1
            CatalogEventType.PRODUCT_UNLIKED -> {
                if (productStat.likeCount > 0L) {
                    productStat.likeCount -= 1
                }
            }
            CatalogEventType.PRODUCT_VIEWED -> productStat.viewCount += 1
        }

        if (message.eventType != CatalogEventType.PRODUCT_VIEWED) {
            productStat.latestEventVersion = message.version
        }
        productStatRepository.save(productStat)
    }

    private fun recordUserAction(message: CatalogEventMessage) {
        userActionLogRepository.save(
            UserActionLog(
                eventId = message.eventId,
                actionType = when (message.eventType) {
                    CatalogEventType.PRODUCT_LIKED -> UserActionType.PRODUCT_LIKED
                    CatalogEventType.PRODUCT_UNLIKED -> UserActionType.PRODUCT_UNLIKED
                    CatalogEventType.PRODUCT_VIEWED -> UserActionType.PRODUCT_VIEWED
                },
                memberId = message.memberId,
                aggregateId = message.aggregateId,
                productId = message.productId,
                occurredAt = message.occurredAt,
            ),
        )
    }
}
