package com.loopers.domain.queue

interface OrderQueueRepository {
    /** 대기열 진입(중복 시 기존 순번 유지). 반환: 1-based 순번. */
    fun enter(userId: Long, nowMillis: Long): Long

    /** 0-based 순번. 대기 중이 아니면 null. */
    fun rank(userId: Long): Long?

    /** 전체 대기 인원(ZCARD). */
    fun total(): Long

    /** processing에 등록된 시각이 beforeMillis 이전인 항목을 회수(만료 처리)한다. */
    fun pruneExpiredProcessing(beforeMillis: Long)

    /** 현재 processing 중인(입장 토큰 발급된) 인원 수(ZCARD). */
    fun countActive(): Long

    /** 대기열 앞에서 count명을 원자적으로 꺼낸다(ZPOPMIN). count<=0이면 빈 리스트. */
    fun popNext(count: Int): List<Long>

    /** 입장 토큰을 발급하고(TTL) processing에 등록한다. */
    fun issueToken(userId: Long, token: String, ttlSeconds: Long, nowMillis: Long)

    /** 발급된 입장 토큰을 조회한다. 없으면 null. */
    fun findToken(userId: Long): String?

    /** 입장 토큰을 소비(삭제)하고 processing에서 제거한다. */
    fun consume(userId: Long)
}
