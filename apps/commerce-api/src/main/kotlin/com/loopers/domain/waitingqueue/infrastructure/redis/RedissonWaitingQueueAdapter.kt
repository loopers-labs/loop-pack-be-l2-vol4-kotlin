package com.loopers.domain.waitingqueue.infrastructure.redis

import com.loopers.domain.waitingqueue.config.WaitingQueueProperties
import com.loopers.domain.waitingqueue.constant.WaitingQueueErrorMessages
import com.loopers.domain.waitingqueue.infrastructure.redis.constant.WaitingQueueRedisConstants
import com.loopers.domain.waitingqueue.infrastructure.redis.constant.WaitingQueueRedisScripts
import com.loopers.domain.waitingqueue.model.AdmissionTokenCandidate
import com.loopers.domain.waitingqueue.model.WaitingQueueEntryModel
import com.loopers.domain.waitingqueue.model.WaitingQueueState
import com.loopers.domain.waitingqueue.model.WaitingQueueStatus
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import com.loopers.domain.waitingqueue.port.WaitingQueuePort
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedissonWaitingQueueAdapter(
    private val redissonClient: RedissonClient,
    private val properties: WaitingQueueProperties,
) : WaitingQueuePort {
    override fun findState(userId: Long, now: Instant): WaitingQueueState? = execute {
        val values = redissonClient.script.eval<List<Any>>(
            RScript.Mode.READ_ONLY,
            WaitingQueueRedisScripts.FIND_STATE,
            RScript.ReturnType.MULTI,
            listOf(entriesKey(), userAdmissionKey(userId)),
            userId.toString(),
        )
        decodeState(userId, now, values)
    }

    override fun findPosition(userId: Long): Long? = execute {
        redissonClient.getScoredSortedSet<String>(entriesKey())
            .rank(userId.toString())
            ?.toLong()
            ?.plus(1)
    }

    override fun enqueueIfAbsent(userId: Long, now: Instant): WaitingQueueEntryModel = execute {
        repeat(WaitingQueueRedisConstants.ENQUEUE_STATE_RETRY_LIMIT) {
            val sequence = redissonClient.script.eval<Long>(
                RScript.Mode.READ_WRITE,
                WaitingQueueRedisScripts.ENQUEUE,
                RScript.ReturnType.INTEGER,
                listOf(entriesKey(), sequenceKey(), userAdmissionKey(userId)),
                userId.toString(),
            )
            if (sequence > 0) {
                return@execute waitingEntry(userId, sequence)
            }
            findState(userId, now)?.entry?.let { return@execute it }
        }
        throw DataAccessResourceFailureException(WaitingQueueRedisConstants.ENQUEUE_STATE_RACE_MESSAGE)
    }

    override fun admitNext(
        candidates: List<AdmissionTokenCandidate>,
        tokenTtl: Duration,
        now: Instant,
    ): List<WaitingQueueEntryModel> = execute {
        if (candidates.isEmpty()) {
            return@execute emptyList()
        }
        val arguments = mutableListOf<Any>(
            candidates.size.toString(),
            userAdmissionKeyPrefix(),
            tokenAdmissionKeyPrefix(),
            tokenTtl.toMillis().toString(),
        )
        candidates.forEach { candidate ->
            arguments += candidate.token
            arguments += candidate.availableAt.toEpochMilli().toString()
            arguments += candidate.expiresAt.toEpochMilli().toString()
        }
        val values = redissonClient.script.eval<List<Any>>(
            RScript.Mode.READ_WRITE,
            WaitingQueueRedisScripts.ADMIT,
            RScript.ReturnType.MULTI,
            listOf(entriesKey()),
            *arguments.toTypedArray(),
        )
        values.chunked(2).map { (userId, sequence) ->
            waitingEntry(
                userId = userId.asLong(),
                sequence = sequence.asLong(),
                status = WaitingQueueStatus.ADMITTED,
            )
        }
    }

    override fun validateToken(
        userId: Long,
        token: String,
        idempotencyKey: String,
        now: Instant,
    ): TokenValidationResult = execute {
        if (token.isBlank()) {
            return@execute TokenValidationResult.invalid()
        }
        val result = redissonClient.script.eval<Long>(
            RScript.Mode.READ_WRITE,
            WaitingQueueRedisScripts.VALIDATE_AND_RESERVE_TOKEN,
            RScript.ReturnType.INTEGER,
            listOf(tokenAdmissionKey(token), userAdmissionKey(userId)),
            userId.toString(),
            idempotencyKey,
            token,
            now.toEpochMilli().toString(),
        )
        when (result) {
            WaitingQueueRedisConstants.TOKEN_VALID -> TokenValidationResult.valid()
            WaitingQueueRedisConstants.TOKEN_PROCESSING_BY_SAME_KEY ->
                TokenValidationResult.processingBySameIdempotencyKey(idempotencyKey)
            WaitingQueueRedisConstants.TOKEN_CONSUMED_BY_SAME_KEY ->
                TokenValidationResult.consumedBySameIdempotencyKey(idempotencyKey)
            WaitingQueueRedisConstants.TOKEN_RESERVED_OR_CONSUMED_BY_OTHER_KEY ->
                TokenValidationResult.consumedByDifferentIdempotencyKey(null)
            WaitingQueueRedisConstants.TOKEN_NOT_YET_AVAILABLE -> TokenValidationResult.notYetAvailable()
            else -> TokenValidationResult.invalid()
        }
    }

    override fun consumeToken(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ): Boolean = execute {
            redissonClient.script.eval<Long>(
                RScript.Mode.READ_WRITE,
                WaitingQueueRedisScripts.CONSUME_TOKEN,
                RScript.ReturnType.INTEGER,
                listOf(tokenAdmissionKey(token), userAdmissionKey(userId)),
                userId.toString(),
                idempotencyKey,
            ) == WaitingQueueRedisConstants.TOKEN_TRANSITION_SUCCEEDED
        }

    override fun releaseToken(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ): Boolean = execute {
            redissonClient.script.eval<Long>(
                RScript.Mode.READ_WRITE,
                WaitingQueueRedisScripts.RELEASE_TOKEN,
                RScript.ReturnType.INTEGER,
                listOf(tokenAdmissionKey(token), userAdmissionKey(userId)),
                userId.toString(),
                idempotencyKey,
            ) == WaitingQueueRedisConstants.TOKEN_TRANSITION_SUCCEEDED
        }

    private fun decodeState(
        userId: Long,
        now: Instant,
        values: List<Any>,
    ): WaitingQueueState? {
        if (values.isEmpty()) {
            return null
        }
        return when (values.first().toString()) {
            WaitingQueueRedisConstants.WAITING_QUEUE_STATUS -> waitingState(userId, now, values)
            WaitingQueueRedisConstants.ADMITTED_QUEUE_STATUS -> admittedState(userId, now, values)
            else -> null
        }
    }

    private fun waitingState(
        userId: Long,
        now: Instant,
        values: List<Any>,
    ): WaitingQueueState {
        val sequence = values[1].asLong()
        val position = values[2].asLong() + 1
        return WaitingQueueState(
            status = WaitingQueueStatus.WAITING,
            entry = waitingEntry(userId, sequence),
            sequence = sequence,
            position = position,
            totalWaiting = values[3].asLong(),
            estimatedWaitSeconds = estimateWaitSeconds(position),
            recommendedPollingIntervalSeconds = properties.pollingInterval.seconds,
            token = null,
            tokenAvailableAt = null,
            tokenExpiresAt = null,
        )
    }

    private fun admittedState(
        userId: Long,
        now: Instant,
        values: List<Any>,
    ): WaitingQueueState {
        val token = values[1].toString()
        val availableAt = Instant.ofEpochMilli(values[2].asLong())
        val remainingTtlMillis = values[3].asLong()
        val sequence = values[4].asLong()
        return WaitingQueueState(
            status = WaitingQueueStatus.ADMITTED,
            entry = waitingEntry(userId, sequence, WaitingQueueStatus.ADMITTED),
            sequence = sequence,
            position = null,
            totalWaiting = values[5].asLong(),
            estimatedWaitSeconds = 0,
            recommendedPollingIntervalSeconds = properties.pollingInterval.seconds,
            token = token,
            tokenAvailableAt = availableAt,
            tokenExpiresAt = now.plusMillis(remainingTtlMillis),
        ).also {
            require(!availableAt.isAfter(it.tokenExpiresAt)) {
                WaitingQueueErrorMessages.AVAILABLE_AT_AFTER_EXPIRATION
            }
        }
    }

    private fun waitingEntry(
        userId: Long,
        sequence: Long,
        status: WaitingQueueStatus = WaitingQueueStatus.WAITING,
    ): WaitingQueueEntryModel = WaitingQueueEntryModel(
        userId = userId,
        sequence = sequence,
        status = status,
    )

    private fun estimateWaitSeconds(position: Long): Long {
        val cyclesUntilAdmission = ((position - 1).coerceAtLeast(0) / properties.admissionBatchSize) + 1
        val delayMillis = properties.schedulerDelay.toMillis()
        val estimatedMillis = if (cyclesUntilAdmission > Long.MAX_VALUE / delayMillis) {
            Long.MAX_VALUE
        } else {
            cyclesUntilAdmission * delayMillis
        }
        val millisPerSecond = Duration.ofSeconds(1).toMillis()
        return estimatedMillis / millisPerSecond +
            if (estimatedMillis % millisPerSecond == 0L) 0L else 1L
    }

    private fun entriesKey(): String = namespaced(properties.redisKeys.entries)

    private fun sequenceKey(): String = namespaced(properties.redisKeys.sequence)

    private fun userAdmissionKey(userId: Long): String = "${userAdmissionKeyPrefix()}:$userId"

    private fun tokenAdmissionKey(token: String): String = "${tokenAdmissionKeyPrefix()}:$token"

    private fun userAdmissionKeyPrefix(): String = namespaced(properties.redisKeys.userAdmissionPrefix)

    private fun tokenAdmissionKeyPrefix(): String = namespaced(properties.redisKeys.tokenAdmissionPrefix)

    private fun namespaced(key: String): String = "${properties.redisKeyPrefix}:$key"

    private fun Any.asLong(): Long = toString().toLong()

    private fun <T> execute(action: () -> T): T = try {
        action()
    } catch (e: RedisException) {
        throw DataAccessResourceFailureException(WaitingQueueRedisConstants.OPERATION_FAILED_MESSAGE, e)
    }
}
