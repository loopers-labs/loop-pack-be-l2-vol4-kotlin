package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EntryTokenGate(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
) {
    private val log = LoggerFactory.getLogger(EntryTokenGate::class.java)

    fun validate(loginId: String, password: String, token: String?): Long {
        val user = userService.getProfile(loginId = loginId, password = password)
        val stored = try {
            queueRepository.findToken(user.id)
        } catch (e: Exception) {
            // fail-open: Redis 장애 시 게이트 우회(서비스 유지 우선). 경보.
            log.warn("Redis unavailable — bypassing entry-token gate (fail-open). userId={}", user.id, e)
            return user.id
        }
        if (token.isNullOrBlank() || stored == null || stored != token) {
            throw CoreException(ErrorType.TOO_MANY_REQUESTS)
        }
        return user.id
    }

    fun consume(userId: Long) {
        runCatching { queueRepository.consume(userId) }
            .onFailure { log.warn("Failed to consume entry token. userId={}", userId, it) }
    }
}
