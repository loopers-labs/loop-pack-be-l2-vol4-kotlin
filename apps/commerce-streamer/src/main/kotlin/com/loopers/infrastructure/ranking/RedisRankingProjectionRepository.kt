package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.CatalogRankingProjection
import com.loopers.domain.ranking.OrderRankingProjection
import com.loopers.domain.ranking.RankingProjectionRepository
import com.loopers.domain.ranking.RankingProjectionResult
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
class RedisRankingProjectionRepository(
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val properties: RankingRedisProperties,
) : RankingProjectionRepository {
    private val catalogScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/ranking-project-catalog.lua"))
        resultType = Long::class.java
    }
    private val orderScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/ranking-project-order.lua"))
        resultType = Long::class.java
    }

    override fun projectCatalog(command: CatalogRankingProjection): RankingProjectionResult {
        val date = command.date
        val metricKey = when (command.metric) {
            com.loopers.domain.ranking.CatalogRankingMetric.VIEW -> RankingRedisKeys.view(date)
            com.loopers.domain.ranking.CatalogRankingMetric.LIKE -> RankingRedisKeys.like(date)
        }
        val keys = listOf(
            RankingRedisKeys.processed(date),
            metricKey,
            RankingRedisKeys.view(date),
            RankingRedisKeys.like(date),
            RankingRedisKeys.sales(date),
            RankingRedisKeys.carry(date),
            RankingRedisKeys.all(date),
            RankingRedisKeys.ACTIVE_WEIGHTS,
        )
        val result = redisTemplate.execute(
            catalogScript,
            keys,
            command.eventId,
            command.productId.toString(),
            command.delta.toString(),
            command.expiresAt.epochSecond.toString(),
            properties.viewWeight.toString(),
            properties.likeWeight.toString(),
            properties.salesWeight.toString(),
        )
        return result.toProjectionResult()
    }

    override fun projectOrder(command: OrderRankingProjection): RankingProjectionResult {
        require(command.items.isNotEmpty()) { "Payment succeeded ranking items must not be empty." }
        val date = command.date
        val keys = listOf(
            RankingRedisKeys.processed(date),
            RankingRedisKeys.rawSalesAmount(date),
            RankingRedisKeys.sales(date),
            RankingRedisKeys.view(date),
            RankingRedisKeys.like(date),
            RankingRedisKeys.carry(date),
            RankingRedisKeys.all(date),
            RankingRedisKeys.ACTIVE_WEIGHTS,
        )
        val args = buildList {
            add(command.eventId)
            add(command.expiresAt.epochSecond.toString())
            add(properties.viewWeight.toString())
            add(properties.likeWeight.toString())
            add(properties.salesWeight.toString())
            command.items.forEach { item ->
                add(item.productId.toString())
                add(item.amount.toString())
            }
        }
        val result = redisTemplate.execute(orderScript, keys, *args.toTypedArray())
        return result.toProjectionResult()
    }

    private fun Long?.toProjectionResult(): RankingProjectionResult {
        return if (this == 1L) {
            RankingProjectionResult.APPLIED
        } else {
            RankingProjectionResult.DUPLICATE
        }
    }
}
