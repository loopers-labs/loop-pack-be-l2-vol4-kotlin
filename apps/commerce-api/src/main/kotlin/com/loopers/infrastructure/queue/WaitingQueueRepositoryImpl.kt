package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.WaitingQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Redis Sorted Set 기반 대기열 저장소.
 *
 * 순번(ZRANK) 조회는 반드시 master 노드에서 읽어야 한다.
 * 기본 RedisTemplate 은 REPLICA_PREFERRED 라, 방금 ZADD 한 값이 복제본에
 * 아직 전파되지 않아 순번이 비거나 어긋나는 read-your-write 문제가 생긴다.
 */
@Repository
class WaitingQueueRepositoryImpl(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) redisTemplate: RedisTemplate<*, *>,
) : WaitingQueueRepository {
    @Suppress("UNCHECKED_CAST")
    private val redis = redisTemplate as RedisTemplate<String, String>

    override fun enter(userId: Long, at: Instant) {
        // ZADD NX : 이미 대기 중인 member 면 score 를 갱신하지 않아 최초 순번이 유지된다.
        redis.opsForZSet().addIfAbsent(KEY, userId.toString(), at.toEpochMilli().toDouble())
    }

    override fun position(userId: Long): Long? =
        redis.opsForZSet().rank(KEY, userId.toString())

    override fun size(): Long =
        redis.opsForZSet().zCard(KEY) ?: 0L

    override fun poll(count: Long): List<Long> {
        if (count <= 0) return emptyList()
        val popped = redis.opsForZSet().popMin(KEY, count) ?: return emptyList()
        return popped.mapNotNull { it.value?.toLongOrNull() }
    }

    companion object {
        private const val KEY = "waiting-queue"
    }
}
