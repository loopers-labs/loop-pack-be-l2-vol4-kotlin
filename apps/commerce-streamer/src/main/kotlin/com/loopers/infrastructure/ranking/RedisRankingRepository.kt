package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 랭킹판 Redis 어댑터. 점수 누적은 master 에 즉시 반영한다.
 *
 * 멱등 표식(SETNX)과 점수 증분(ZINCRBY)을 하나의 Lua 로 원자 실행한다 — 처음 보는 이벤트일 때만 증분한다.
 * 두 연산이 쪼개지지 않으므로 재전달에도 표식만 남거나 증분만 되는 어긋남이 없다(유실·중복 없음).
 */
@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun incrementScoreOnce(eventId: UUID, key: String, productId: Long, delta: Double, ttlSeconds: Long) {
        masterTemplate.execute(
            INCREMENT_ONCE,
            listOf(seenKey(eventId), key),
            delta.toString(),
            productId.toString(),
            ttlSeconds.toString(),
        )
    }

    private fun seenKey(eventId: UUID): String = "rank:seen:$eventId"

    companion object {
        // KEYS[1]=멱등 표식 키, KEYS[2]=랭킹판 키, ARGV[1]=증분 점수, ARGV[2]=상품 식별자(member), ARGV[3]=보존 기간(초)
        private val INCREMENT_ONCE = DefaultRedisScript(
            """
            if redis.call('SETNX', KEYS[1], '1') == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                redis.call('ZINCRBY', KEYS[2], ARGV[1], ARGV[2])
                redis.call('EXPIRE', KEYS[2], ARGV[3])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
