package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Redis ZSET 기반 랭킹 Repository 구현체.
 *
 * 키 전략: ranking:all:{yyyyMMdd}
 * TTL: 2일 (일간 집계이므로 당일 + 전일 조회를 위해 2일 유지)
 * write 시점에 정렬이 완료되어 read 레이턴시가 O(log N)으로 보장된다.
 */
@Repository
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {

    /**
     * 오늘자 랭킹 키에 점수를 누적한다 (ZINCRBY).
     * 키가 새로 생성되면 TTL을 설정한다.
     */
    override fun incrementScore(productId: Long, score: Double) {
        val key = todayKey()
        val isNew = redisTemplate.opsForZSet().incrementScore(key, productId.toString(), score)

        // 키가 처음 생성되었을 때 TTL 설정
        if (redisTemplate.getExpire(key) == -1L) {
            redisTemplate.expire(key, KEY_TTL)
        }
    }

    private fun todayKey(): String {
        val today = LocalDate.now().format(DATE_FORMAT)
        return "$KEY_PREFIX$today"
    }

    companion object {
        const val KEY_PREFIX = "ranking:all:"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val KEY_TTL = Duration.ofDays(2)
    }
}
