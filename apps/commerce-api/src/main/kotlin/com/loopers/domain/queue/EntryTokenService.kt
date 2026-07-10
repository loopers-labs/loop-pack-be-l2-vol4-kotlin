package com.loopers.domain.queue

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 입장 토큰 도메인 서비스.
 *
 * 토큰 값(랜덤 UUID)과 TTL 정책(5분)을 이 계층에서 결정하고, 저장/검증은 저장소에 위임한다.
 */
@Component
class EntryTokenService(
    private val entryTokenRepository: EntryTokenRepository,
) {
    /** 입장 토큰을 발급하고 토큰 값을 반환한다. */
    fun issue(userId: Long): String {
        val token = UUID.randomUUID().toString()
        entryTokenRepository.save(userId, token, Instant.now().plus(TOKEN_TTL))
        return token
    }

    /** 토큰을 검증하고 원자적으로 소비한다. 유효하지 않으면 false. */
    fun consume(userId: Long, token: String): Boolean =
        entryTokenRepository.consume(userId, token)

    /** 현재 활성 토큰 수(만료분 정리 후). */
    fun activeCount(): Long = entryTokenRepository.activeCount(Instant.now())

    /** 발급된 입장 토큰을 조회한다(소비하지 않음). 없으면 null. */
    fun find(userId: Long): String? = entryTokenRepository.find(userId)

    companion object {
        val TOKEN_TTL: Duration = Duration.ofMinutes(5)
    }
}
