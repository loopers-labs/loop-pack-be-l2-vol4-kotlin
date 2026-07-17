package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val publisher: OutboxEventPublisher,
    private val properties: OutboxRelayProperties,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun publishOnce(now: ZonedDateTime = ZonedDateTime.now()): Int {
        val events = claimPublishableEvents(now)
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Outbox relay must publish outside an active transaction."
        }
        return events.count { event -> publishAndMark(event, now) }
    }

    private fun claimPublishableEvents(now: ZonedDateTime): List<OutboxEventModel> =
        transactionTemplate.execute {
            outboxRepository.claimPublishable(
                now = now,
                claimExpiredBefore = now.minus(properties.relayClaimLease),
                limit = properties.relayBatchSize,
            )
        } ?: emptyList()

    private fun publishAndMark(
        event: OutboxEventModel,
        now: ZonedDateTime,
    ): Boolean =
        try {
            publisher.publish(event)
            markPublished(event, now)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            markFailed(event, e, now)
            false
        } catch (e: Exception) {
            markFailed(event, e, now)
            false
        }

    private fun markPublished(
        event: OutboxEventModel,
        publishedAt: ZonedDateTime,
    ): Boolean = transactionTemplate.execute {
        outboxRepository.markPublished(
            eventId = event.eventId,
            claimId = checkNotNull(event.claimId) { "Claimed outbox event must have a claimId." },
            publishedAt = publishedAt,
        )
    } ?: false

    private fun markFailed(
        event: OutboxEventModel,
        cause: Exception,
        now: ZonedDateTime,
    ) {
        val marked = transactionTemplate.execute {
            outboxRepository.markFailed(
                eventId = event.eventId,
                claimId = checkNotNull(event.claimId) { "Claimed outbox event must have a claimId." },
                error = cause.message ?: cause::class.java.simpleName,
                nextRetryAt = now.plus(properties.relayRetryDelay),
                maxPublishAttempts = properties.maxPublishAttempts,
            )
        } ?: false
        val publishAttempts = event.retryCount + 1
        if (marked && publishAttempts >= properties.maxPublishAttempts) {
            log.error(
                "Outbox event moved to DEAD after {} publish attempts. eventId={}, type={}, topic={}",
                publishAttempts,
                event.eventId,
                event.type,
                event.topicName,
                cause,
            )
        }
    }
}
