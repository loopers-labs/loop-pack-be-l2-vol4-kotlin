package com.loopers.domain.queue

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class WaitingQueueAdmissionService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
) {
    fun admit(count: Long): Int {
        val admittedUserIds = waitingQueueRepository.pop(count)

        admittedUserIds.forEach { userId ->
            entryTokenRepository.save(userId, EntryToken.issue())
        }

        return admittedUserIds.size
    }

    fun verify(userId: Long, token: String?) {
        if (token.isNullOrBlank()) {
            throw CoreException(ErrorType.FORBIDDEN, "입장 토큰이 없습니다. 대기열 입장 순서를 기다려 주세요.")
        }

        val issuedToken = entryTokenRepository.find(userId)
            ?: throw CoreException(ErrorType.FORBIDDEN, "유효한 입장 토큰이 없습니다. 대기열 입장 순서를 기다려 주세요.")

        if (issuedToken.value != token) {
            throw CoreException(ErrorType.FORBIDDEN, "입장 토큰이 일치하지 않습니다.")
        }
    }

    fun completeEntry(userId: Long) {
        entryTokenRepository.delete(userId)
    }
}
