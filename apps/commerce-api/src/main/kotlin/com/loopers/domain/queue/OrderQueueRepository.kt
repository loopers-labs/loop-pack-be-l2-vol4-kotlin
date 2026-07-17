package com.loopers.domain.queue

/**
 * 주문 대기열 저장소 인터페이스.
 * Redis Sorted Set + String 기반으로 대기열과 토큰을 관리한다.
 */
interface OrderQueueRepository {
    /** 대기열에 사용자를 추가한다 (score = 현재 시각). */
    fun addToQueue(userId: Long)

    /** 사용자가 이미 대기열에 있는지 확인한다. */
    fun isInQueue(userId: Long): Boolean

    /** 사용자의 현재 순번(0-based rank)을 조회한다. 없으면 null. */
    fun getRank(userId: Long): Long?

    /** 전체 대기 인원을 조회한다. */
    fun getTotalWaiting(): Long

    /** 대기열 앞에서 N명을 꺼낸다 (ZPOPMIN). */
    fun popFromQueue(count: Int): List<Long>

    /** 입장 토큰을 발급한다 (TTL 5분). */
    fun issueToken(userId: Long): String

    /** 사용자의 토큰을 조회한다. 없으면 null. */
    fun getToken(userId: Long): String?

    /** 토큰을 삭제한다 (주문 완료 후). */
    fun deleteToken(userId: Long)
}
