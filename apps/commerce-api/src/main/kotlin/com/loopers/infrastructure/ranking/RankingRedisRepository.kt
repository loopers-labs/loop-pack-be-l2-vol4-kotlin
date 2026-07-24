package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankedProduct
import com.loopers.domain.ranking.RankingQueryRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RankingRedisRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingQueryRepository {
    override fun page(key: String, offset: Long, size: Long): List<RankedProduct> {
        if (size <= 0) return emptyList()
        return redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, offset + size - 1)
            ?.mapNotNull { tuple ->
                val productId = tuple.value?.toLongOrNull() ?: return@mapNotNull null
                RankedProduct(productId = productId, score = tuple.score ?: 0.0)
            }
            ?: emptyList()
    }

    override fun total(key: String): Long = redisTemplate.opsForZSet().size(key) ?: 0L

    override fun rank(key: String, productId: Long): Long? =
        redisTemplate.opsForZSet().reverseRank(key, productId.toString())
}
