package com.loopers.domain.waitingqueue.application.service

import com.loopers.domain.waitingqueue.config.WaitingQueueProperties
import com.loopers.domain.waitingqueue.constant.WaitingQueueErrorMessages
import com.loopers.domain.waitingqueue.model.AdmissionBatchResult
import com.loopers.domain.waitingqueue.model.AdmissionTokenCandidate
import com.loopers.domain.waitingqueue.model.WaitingQueueState
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import com.loopers.domain.waitingqueue.port.WaitingQueuePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Component
class WaitingQueueService(
    private val port: WaitingQueuePort,
    private val properties: WaitingQueueProperties,
    private val clock: Clock,
    private val tokenGenerator: () -> String,
    private val admissionJitter: (Int) -> Duration,
) {
    private val log = LoggerFactory.getLogger(WaitingQueueService::class.java)

    @Autowired
    constructor(
        port: WaitingQueuePort,
        properties: WaitingQueueProperties,
    ) : this(
        port = port,
        properties = properties,
        clock = Clock.systemUTC(),
        tokenGenerator = { "${properties.tokenPrefix}${UUID.randomUUID()}" },
        admissionJitter = { index ->
            val jitterMaxMillis = properties.schedulerJitterMax.toMillis()
            if (jitterMaxMillis <= 0) {
                Duration.ZERO
            } else {
                Duration.ofMillis(
                    jitterMaxMillis * index / properties.admissionBatchSize.coerceAtLeast(1),
                )
            }
        },
    )

    fun enter(userId: Long): WaitingQueueState =
        mapStoreUnavailable {
            val now = clock.instant()
            port.findState(userId, now)?.let { return@mapStoreUnavailable it }
            port.enqueueIfAbsent(userId, now)
            port.findState(userId, now) ?: throw CoreException(ErrorType.NOT_FOUND)
        }

    fun position(userId: Long): WaitingQueueState =
        mapStoreUnavailable {
            port.findState(userId, clock.instant()) ?: throwQueueEntryNotFound(userId)
        }

    fun admitBatch(): AdmissionBatchResult =
        mapStoreUnavailable {
            val now = clock.instant()
            val candidates = List(properties.admissionBatchSize) { index ->
                AdmissionTokenCandidate(
                    token = tokenGenerator(),
                    availableAt = now.plus(admissionJitter(index)),
                    expiresAt = now.plus(properties.tokenTtl),
                )
            }
            val admittedUserIds = port.admitNext(candidates, properties.tokenTtl, now)
                .map { it.userId }
            AdmissionBatchResult(
                admittedCount = admittedUserIds.size,
                admittedUserIds = admittedUserIds,
            )
        }

    fun validateForOrder(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ) = mapStoreUnavailable {
            val result = port.validateToken(userId, token, idempotencyKey, clock.instant())
            when {
                result.status == TokenValidationResult.Status.NOT_YET_AVAILABLE ->
                    throw CoreException(
                        ErrorType.CONFLICT,
                        WaitingQueueErrorMessages.TOKEN_NOT_YET_AVAILABLE,
                    )
                !result.isAllowed -> throw CoreException(ErrorType.UNAUTHORIZED)
            }
            result
        }

    fun consumeAfterOrderCreated(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ) {
        mapStoreUnavailable {
            if (!port.consumeToken(userId, token, idempotencyKey)) {
                throw CoreException(
                    ErrorType.SERVICE_UNAVAILABLE,
                    WaitingQueueErrorMessages.TOKEN_CONSUME_TRANSITION_FAILED,
                )
            }
        }
    }

    fun releaseAfterOrderFailed(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ) {
        mapStoreUnavailable {
            if (!port.releaseToken(userId, token, idempotencyKey)) {
                throw CoreException(
                    ErrorType.SERVICE_UNAVAILABLE,
                    WaitingQueueErrorMessages.TOKEN_RELEASE_TRANSITION_FAILED,
                )
            }
        }
    }

    private fun <T> mapStoreUnavailable(action: () -> T): T {
        return try {
            action()
        } catch (e: DataAccessResourceFailureException) {
            throwStoreUnavailable(e)
        } catch (e: TransientDataAccessResourceException) {
            throwStoreUnavailable(e)
        }
    }

    private fun throwStoreUnavailable(cause: RuntimeException): Nothing {
        throw CoreException(ErrorType.SERVICE_UNAVAILABLE, cause = cause)
    }

    private fun throwQueueEntryNotFound(userId: Long): Nothing {
        log.info("Waiting queue entry was not found. userId={}", userId)
        throw CoreException(
            errorType = ErrorType.NOT_FOUND,
            customMessage = WaitingQueueErrorMessages.ENTRY_NOT_FOUND,
        )
    }
}
