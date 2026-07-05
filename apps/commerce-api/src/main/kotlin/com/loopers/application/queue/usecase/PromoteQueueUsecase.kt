package com.loopers.application.queue.usecase

import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PromoteQueueUsecase(
    private val queueRepository: OrderQueueRepository,
    @Value("\${queue.capacity:50}") private val capacity: Long,
    @Value("\${queue.rate-batch:18}") private val rateBatch: Int,
    @Value("\${queue.token-ttl-seconds:300}") private val ttlSeconds: Long,
) {
    // 용량 기반 leaky bucket 1 tick. 만료 프룬 → active 산정 → min(rateBatch, capacity-active) 발급.
    fun promoteOnce(nowMillis: Long): Int {
        queueRepository.pruneExpiredProcessing(beforeMillis = nowMillis - ttlSeconds * 1000)
        val active = queueRepository.countActive()
        val free = (capacity - active).coerceAtLeast(0)
        val admit = minOf(rateBatch.toLong(), free).toInt()
        if (admit <= 0) return 0
        val users = queueRepository.popNext(admit)
        users.forEach { userId ->
            queueRepository.issueToken(userId, UUID.randomUUID().toString(), ttlSeconds, nowMillis)
        }
        return users.size
    }
}
