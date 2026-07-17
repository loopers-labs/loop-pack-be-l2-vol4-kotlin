package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingQueryRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

/**
 * Redis ZSET 기반 랭킹 조회 Repository.
 * ZREVRANGE로 Top-N을, ZREVRANK로 개별 순위를 조회한다.
 * read 레이턴시: O(log N + M) (M = 조회 건수)
 */
@Repository
class RedisRankingQueryRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingQueryRepository {

    /**
     * 점수 높은 순으로 Top-N을 조회한다 (ZREVRANGE).
     */
    override fun getTopN(date: String, offset: Long, size: Long): List<RankingEntry> {
        val key = rankingKey(date)
        val results = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, offset + size - 1)
            ?: return emptyList()

        return results.mapNotNull { tuple ->
            val productId = tuple.value?.toLongOrNull() ?: return@mapNotNull null
            val score = tuple.score ?: 0.0
            RankingEntry(productId = productId, score = score)
        }
    }

    /**
     * 특정 상품의 순위를 조회한다 (ZREVRANK). 1-based로 반환.
     */
    override fun getRank(date: String, productId: Long): Long? {
        val key = rankingKey(date)
        val rank = redisTemplate.opsForZSet().reverseRank(key, productId.toString())
        return rank?.plus(1)
    }

    /**
     * 특정 상품의 점수를 조회한다 (ZSCORE).
     */
    override fun getScore(date: String, productId: Long): Double? {
        val key = rankingKey(date)
        return redisTemplate.opsForZSet().score(key, productId.toString())
    }

    private fun rankingKey(date: String) = "$KEY_PREFIX$date"

    companion object {
        private const val KEY_PREFIX = "ranking:all:"
    }
}
