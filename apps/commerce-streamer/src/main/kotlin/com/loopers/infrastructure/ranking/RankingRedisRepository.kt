package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScoreEntry
import com.loopers.domain.ranking.RankingWindow
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.connection.RedisStringCommands
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RankingRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun applyAll(entries: List<RankingScoreEntry>, window: RankingWindow): Int {
        if (entries.isEmpty()) return 0
        val fresh = dedupPass(entries)
        if (fresh.isEmpty()) return 0
        incrementPass(fresh, window)
        return fresh.size
    }

    // pass 1: 이벤트별 SET NX 파이프라인 — 재소비(중복) 이벤트를 걸러낸다.
    private fun dedupPass(entries: List<RankingScoreEntry>): List<RankingScoreEntry> {
        val results = redisTemplate.executePipelined { connection ->
            entries.forEach { entry ->
                connection.stringCommands().set(
                    raw(DEDUP_KEY_PREFIX + entry.eventId),
                    raw("1"),
                    Expiration.from(DEDUP_TTL),
                    RedisStringCommands.SetOption.SET_IF_ABSENT,
                )
            }
            null
        }
        return entries.filterIndexed { index, _ -> results[index] == true }
    }

    // pass 2: 통과 엔트리만 daily/hourly ZINCRBY + 윈도우 절대시각 만료.
    private fun incrementPass(entries: List<RankingScoreEntry>, window: RankingWindow) {
        redisTemplate.executePipelined { connection ->
            entries.flatMap { it.deltas }.forEach { delta ->
                val member = raw(delta.productId.toString())
                connection.zSetCommands().zIncrBy(raw(window.dailyKey), delta.score, member)
                connection.zSetCommands().zIncrBy(raw(window.hourlyKey), delta.score, member)
            }
            connection.keyCommands().pExpireAt(raw(window.dailyKey), window.dailyExpireAt.toEpochMilli())
            connection.keyCommands().pExpireAt(raw(window.hourlyKey), window.hourlyExpireAt.toEpochMilli())
            null
        }
    }

    private fun raw(value: String): ByteArray = redisTemplate.stringSerializer.serialize(value)!!

    companion object {
        const val DEDUP_KEY_PREFIX = "ranking:handled:v1:"
        private val DEDUP_TTL = Duration.ofDays(2)
    }
}
