package com.loopers.domain.queue

interface OrderQueueRepository {
    /** 대기열 진입(중복 시 기존 순번 유지). 반환: 1-based 순번. */
    fun enter(userId: Long, nowMillis: Long): Long

    /** 0-based 순번. 대기 중이 아니면 null. */
    fun rank(userId: Long): Long?

    /** 전체 대기 인원(ZCARD). */
    fun total(): Long
}
