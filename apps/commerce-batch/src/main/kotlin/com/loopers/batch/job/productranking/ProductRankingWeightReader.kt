package com.loopers.batch.job.productranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.config.redis.RedisConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class ProductRankingWeightReader(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val properties: RankingRedisProperties,
) {
    fun read(): ProductRankingWeights {
        val hashOperations = redisTemplate.opsForHash<String, String>()
        return ProductRankingWeights(
            view = hashOperations.get(RankingRedisKeys.ACTIVE_WEIGHTS, "view")?.toDoubleOrNull()
                ?: properties.viewWeight,
            like = hashOperations.get(RankingRedisKeys.ACTIVE_WEIGHTS, "like")?.toDoubleOrNull()
                ?: properties.likeWeight,
            sales = hashOperations.get(RankingRedisKeys.ACTIVE_WEIGHTS, "sales")?.toDoubleOrNull()
                ?: properties.salesWeight,
        )
    }
}
