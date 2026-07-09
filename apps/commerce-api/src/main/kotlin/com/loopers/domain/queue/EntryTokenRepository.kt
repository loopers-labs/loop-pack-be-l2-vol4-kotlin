package com.loopers.domain.queue

import java.time.Instant

/**
 * 입장 토큰 저장소 포트.
 *
 * - `entry-token:{userId}` (String) : 토큰 값. 만료 시각까지 유효(자동 만료).
 * - `active-tokens` (Sorted Set) : member=userId, score=만료 epoch. 현재 활성 토큰 수 추적/정리용.
 */
interface EntryTokenRepository {
    /** 입장 토큰을 저장하고 활성 집합에 등록한다. (String + ZADD) */
    fun save(userId: Long, token: String, expiresAt: Instant)

    /**
     * 토큰이 일치할 때만 원자적으로 소비한다(삭제 + 활성 집합에서 제거).
     * 불일치/부재면 아무것도 지우지 않고 false. (Lua compare-and-delete)
     */
    fun consume(userId: Long, token: String): Boolean

    /** 만료분(score ≤ now)을 정리한 뒤 현재 활성 토큰 수. (ZREMRANGEBYSCORE + ZCARD) */
    fun activeCount(now: Instant): Long

    /** 저장된 토큰을 조회한다(소비하지 않음). 없으면 null. (GET) */
    fun find(userId: Long): String?
}
