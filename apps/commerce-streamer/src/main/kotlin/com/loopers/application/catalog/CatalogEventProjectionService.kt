package com.loopers.application.catalog

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.product.ProductStatProjection
import com.loopers.domain.product.ProductStatProjectionRepository
import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionLogRepository
import com.loopers.domain.useraction.UserActionType
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CatalogEventProjectionService(
    private val eventHandledRepository: EventHandledRepository,
    private val productStatProjectionRepository: ProductStatProjectionRepository,
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
        val current = productStatProjectionRepository.findByProductIdForUpdate(message.productId)

        if (current != null && current.latestEventVersion > message.version) {
            return
        }

        val productStat = current ?: ProductStatProjection(
            productId = message.productId,
            brandId = message.brandId ?: 0L,
            likeCount = 0L,
            salesCount = 0L,
            latestEventVersion = 0L,
        )

        when (message.eventType) {
            CatalogEventType.PRODUCT_LIKED -> productStat.likeCount += 1
            CatalogEventType.PRODUCT_UNLIKED -> {
                if (productStat.likeCount > 0L) {
                    productStat.likeCount -= 1
                }
            }
        }

        productStat.latestEventVersion = message.version
        productStatProjectionRepository.save(productStat)
    }

    private fun recordUserAction(message: CatalogEventMessage) {
        userActionLogRepository.save(
            UserActionLog(
                eventId = message.eventId,
                actionType = when (message.eventType) {
                    CatalogEventType.PRODUCT_LIKED -> UserActionType.PRODUCT_LIKED
                    CatalogEventType.PRODUCT_UNLIKED -> UserActionType.PRODUCT_UNLIKED
                },
                memberId = message.memberId,
                aggregateId = message.aggregateId,
                productId = message.productId,
                occurredAt = message.occurredAt,
            ),
        )
    }
}
