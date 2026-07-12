package com.loopers.application.waitingqueue

import com.loopers.domain.waitingqueue.EntryTokenRepository
import com.loopers.domain.waitingqueue.WaitingQueuePosition
import com.loopers.domain.waitingqueue.WaitingQueueRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class WaitingQueueService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val properties: WaitingQueueProperties,
) {
    fun enter(memberId: Long): WaitingQueuePosition {
        entryTokenRepository.find(memberId)?.let { token ->
            val currentTotalWaitingCount = waitingQueueRepository.count()
            return WaitingQueuePosition.ready(
                token = token,
                currentTotalWaitingCount = currentTotalWaitingCount,
            )
        }

        waitingQueueRepository.enterIfAbsent(
            memberId = memberId,
            score = System.currentTimeMillis().toDouble(),
        )

        val rank = checkNotNull(waitingQueueRepository.rank(memberId)) {
            "waiting queue rank must exist after enter"
        }
        val currentTotalWaitingCount = waitingQueueRepository.count()

        return WaitingQueuePosition.waiting(
            rank = rank,
            currentTotalWaitingCount = currentTotalWaitingCount,
        )
    }

    fun getPosition(memberId: Long): WaitingQueuePosition {
        entryTokenRepository.find(memberId)?.let { token ->
            val currentTotalWaitingCount = waitingQueueRepository.count()
            return WaitingQueuePosition.ready(
                token = token,
                currentTotalWaitingCount = currentTotalWaitingCount,
            )
        }

        val rank = waitingQueueRepository.rank(memberId)
        val currentTotalWaitingCount = waitingQueueRepository.count()

        return rank
            ?.let {
                WaitingQueuePosition.waiting(
                    rank = it,
                    currentTotalWaitingCount = currentTotalWaitingCount,
                )
            }
            ?: WaitingQueuePosition.notEntered(currentTotalWaitingCount)
    }

    fun issueNextEntries(batchSize: Long = properties.scheduler.batchSize): List<String> {
        return waitingQueueRepository.popNext(batchSize)
            .mapNotNull { memberId ->
                val token = generateToken()
                entryTokenRepository.issue(memberId = memberId, token = token, ttl = properties.entryTokenTtl)
                val issuedToken = entryTokenRepository.find(memberId)
                if (issuedToken == null) {
                    // TODO : notification 알림 발송 구현
                }
                issuedToken
            }
    }

    fun validateEntryToken(
        memberId: Long,
        entryToken: String?,
    ) {
        val token = entryToken?.takeIf(String::isNotBlank)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "Entry token is required.")
        val userToken = entryTokenRepository.find(memberId)

        if (token != userToken) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Entry token is invalid or expired.")
        }
    }

    fun deleteEntryToken(memberId: Long) {
        entryTokenRepository.delete(memberId)
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
