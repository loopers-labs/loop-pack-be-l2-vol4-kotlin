package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import java.time.ZonedDateTime
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val publisher: OutboxEventPublisher,
    private val properties: LikeCountOutboxRelayProperties,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun publishOnce(): Int {
        val events = claimPublishableEvents()
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Outbox relay must publish outside an active transaction."
        }
        return events.count { event -> publishAndMark(event) }
    }

    private fun claimPublishableEvents(): List<OutboxEventModel> =
        transactionTemplate.execute {
            outboxRepository.claimPublishable(
                type = LikeCountOutboxRelayProperties.EVENT_TYPE,
                now = ZonedDateTime.now(),
                limit = properties.relayBatchSize,
            )
        } ?: emptyList()

    private fun publishAndMark(event: OutboxEventModel): Boolean =
        try {
            publisher.publish(event)
            markPublished(event)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            markFailed(event, e)
            false
        } catch (e: Exception) {
            markFailed(event, e)
            false
        }

    private fun markPublished(event: OutboxEventModel) {
        transactionTemplate.executeWithoutResult {
            outboxRepository.markPublished(event.eventId, ZonedDateTime.now())
        }
    }

    private fun markFailed(
        event: OutboxEventModel,
        cause: Exception,
    ) {
        transactionTemplate.executeWithoutResult {
            outboxRepository.markFailed(
                eventId = event.eventId,
                error = cause.message ?: cause::class.java.simpleName,
                nextRetryAt = ZonedDateTime.now().plus(properties.relayRetryDelay),
            )
        }
    }
}
