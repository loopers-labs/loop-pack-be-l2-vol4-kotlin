package com.loopers.domain.queue

import java.time.Instant

/**
 * 대기열(Waiting Queue) 저장소 포트.
 *
 * Redis Sorted Set(`waiting-queue`)을 원천 저장소로 사용한다.
 * - score = 진입 시각(epoch millis) → 먼저 들어온 유저가 앞 순번
 * - member = userId → Set 특성상 중복 진입이 자연히 방지됨
 */
interface WaitingQueueRepository {
    /** 대기열에 진입시킨다. 이미 대기 중인 유저면 최초 순번을 유지한다. (ZADD NX) */
    fun enter(userId: Long, at: Instant)

    /** 0-based 순번. 대기열에 없으면 null. (ZRANK) */
    fun position(userId: Long): Long?

    /** 전체 대기 인원. (ZCARD) */
    fun size(): Long

    /** 앞에서 count 명을 꺼내 제거하고 userId 목록을 반환한다. 진입 순서(score 오름차순). (ZPOPMIN) */
    fun poll(count: Long): List<Long>
}
