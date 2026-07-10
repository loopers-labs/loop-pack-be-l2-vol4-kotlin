package com.loopers.application.queue.usecase

import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PromoteQueueUsecase(
    private val queueRepository: OrderQueueRepository,
    @Value("\${queue.capacity:50}") private val capacity: Long,
    @Value("\${queue.refill-per-second:175}") private val refillPerSecond: Long,
    @Value("\${queue.burst:50}") private val burst: Long,
    @Value("\${queue.token-ttl-seconds:300}") private val ttlSeconds: Long,
) {
    // token bucket 잔량. 단일 스케줄러 스레드에서만 갱신된다(다중 인스턴스 시 Redis 이동 필요 — spec §14).
    private var bucketTokens = 0.0
    private var lastRefillMillis: Long? = null

    // token bucket 1 tick: refill → 만료 프룬 → active 산정 → min(버킷 토큰, capacity-active) 발급.
    fun promoteOnce(nowMillis: Long): Int {
        refill(nowMillis)
        queueRepository.pruneExpiredProcessing(beforeMillis = nowMillis - ttlSeconds * 1000)
        val active = queueRepository.countActive()
        val free = (capacity - active).coerceAtLeast(0)
        val admit = minOf(bucketTokens.toLong(), free).toInt()
        if (admit <= 0) return 0
        val users = queueRepository.popNext(admit)
        users.forEach { userId ->
            queueRepository.issueToken(userId, UUID.randomUUID().toString(), ttlSeconds, nowMillis)
        }
        bucketTokens -= users.size // 실제 발급분만 차감(대기 인원이 적으면 잔여 보존)
        return users.size
    }

    // 시작 시 가득 찬 버킷(burst). 경과 시간 × refill rate만큼 채우되 burst를 상한으로 한다.
    private fun refill(nowMillis: Long) {
        val last = lastRefillMillis
        bucketTokens = if (last == null) {
            burst.toDouble()
        } else {
            val refilled = (nowMillis - last).coerceAtLeast(0) * refillPerSecond / 1000.0
            minOf(burst.toDouble(), bucketTokens + refilled)
        }
        lastRefillMillis = nowMillis
    }
}
