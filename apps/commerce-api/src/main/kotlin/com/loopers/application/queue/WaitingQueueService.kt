package com.loopers.application.queue

import com.loopers.domain.queue.EntryTokenRepository
import com.loopers.domain.queue.WaitingQueuePosition
import com.loopers.domain.queue.WaitingQueueRepository
import com.loopers.domain.queue.WaitingQueueStatus
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import kotlin.math.ceil

@Component
class WaitingQueueService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val properties: WaitingQueueProperties,
) {
    fun enter(memberId: Long): WaitingQueuePosition {
        entryTokenRepository.find(memberId)?.let { token ->
            return readyPosition(token)
        }

        waitingQueueRepository.enterIfAbsent(
            memberId = memberId,
            score = System.currentTimeMillis().toDouble(),
        )

        return waitingPosition(memberId)
    }

    fun getPosition(memberId: Long): WaitingQueuePosition {
        entryTokenRepository.find(memberId)?.let { token ->
            return readyPosition(token)
        }

        return waitingQueueRepository.rank(memberId)
            ?.let { rank -> waitingPosition(memberId, rank) }
            ?: WaitingQueuePosition(
                status = WaitingQueueStatus.NOT_ENTERED,
                rank = null,
                totalWaiting = waitingQueueRepository.count(),
                estimatedWaitSeconds = null,
                pollingIntervalSeconds = pollingIntervalSeconds(null),
                entryToken = null,
            )
    }

    fun issueNextEntries(batchSize: Long = properties.scheduler.batchSize): List<String> {
        return waitingQueueRepository.popNext(batchSize)
            .map { memberId ->
                val token = generateToken()
                entryTokenRepository.issue(memberId = memberId, token = token, ttl = properties.entryTokenTtl)
                token
            }
    }

    fun validateEntryToken(memberId: Long, token: String): Boolean {
        return entryTokenRepository.find(memberId) == token
    }

    fun deleteEntryToken(memberId: Long) {
        entryTokenRepository.delete(memberId)
    }

    private fun waitingPosition(
        memberId: Long,
        currentRank: Long? = waitingQueueRepository.rank(memberId),
    ): WaitingQueuePosition {
        val rank = currentRank ?: 0L
        return WaitingQueuePosition(
            status = WaitingQueueStatus.WAITING,
            rank = rank,
            totalWaiting = waitingQueueRepository.count(),
            estimatedWaitSeconds = estimatedWaitSeconds(rank),
            pollingIntervalSeconds = pollingIntervalSeconds(rank),
            entryToken = null,
        )
    }

    private fun readyPosition(token: String): WaitingQueuePosition {
        return WaitingQueuePosition(
            status = WaitingQueueStatus.READY,
            rank = 0,
            totalWaiting = waitingQueueRepository.count(),
            estimatedWaitSeconds = 0,
            pollingIntervalSeconds = 0,
            entryToken = token,
        )
    }

    private fun estimatedWaitSeconds(rank: Long): Long {
        val throughput = properties.estimatedThroughputPerSecond.coerceAtLeast(1)
        return ceil(rank.toDouble() / throughput.toDouble()).toLong()
    }

    private fun pollingIntervalSeconds(rank: Long?): Long {
        return when {
            rank == null -> 5
            rank < 100 -> 1
            rank < 1_000 -> 3
            else -> 5
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private companion object {
        private const val TOKEN_BYTE_LENGTH = 32
        private val secureRandom = SecureRandom()
    }
}
