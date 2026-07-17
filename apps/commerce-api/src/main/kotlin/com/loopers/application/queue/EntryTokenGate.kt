package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EntryTokenGate(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
    @Value("\${queue.token-ttl-seconds:300}") private val ttlSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(EntryTokenGate::class.java)

    /** 토큰 검증=소비를 원자적으로 수행(claim)한다. 동시 중복 요청은 1개만 통과한다. */
    fun claim(loginId: String, password: String, token: String?): Long {
        val user = userService.getProfile(loginId = loginId, password = password)
        val claimed = try {
            queueRepository.claimToken(user.id, token.orEmpty())
        } catch (e: DataAccessException) {
            // fail-open: Redis 장애 시 게이트 우회(서비스 유지 우선). 경보. 그 외 예외는 전파해 오진을 막는다.
            log.warn("Redis unavailable — bypassing entry-token gate (fail-open). userId={}", user.id, e)
            return user.id
        }
        if (!claimed) {
            throw CoreException(ErrorType.TOO_MANY_REQUESTS)
        }
        return user.id
    }

    /** 주문 완료: processing에서 제거해 capacity를 회복한다. */
    fun complete(userId: Long) {
        runCatching { queueRepository.release(userId) }
            .onFailure { log.warn("Failed to release processing slot. userId={}", userId, it) }
    }

    /** 주문 실패: 같은 토큰을 복원해 재시도를 보장한다(claim이 이미 소비했으므로). */
    fun restore(userId: Long, token: String) {
        runCatching { queueRepository.issueToken(userId, token, ttlSeconds, Instant.now().toEpochMilli()) }
            .onFailure { log.warn("Failed to restore entry token. userId={}", userId, it) }
    }
}
