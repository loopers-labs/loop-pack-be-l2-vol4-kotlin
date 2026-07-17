package com.loopers.projection.ranking.infrastructure.redis

import com.loopers.projection.ranking.application.RankingKey
import com.loopers.projection.ranking.infrastructure.redis.constant.RankingRedisScripts
import com.loopers.projection.ranking.port.ProductRankingStore
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

@Component
class RedissonProductRankingStore(
    private val redissonClient: RedissonClient,
) : ProductRankingStore {
    override fun incrementScore(
        date: LocalDate,
        eventId: UUID,
        productId: Long,
        score: Double,
    ): Boolean = execute {
        redissonClient.script.eval<Long>(
            RScript.Mode.READ_WRITE,
            RankingRedisScripts.INCREMENT_SCORE,
            RScript.ReturnType.INTEGER,
            listOf(RankingKey.dedup(eventId, productId), RankingKey.daily(date)),
            score.toString(),
            productId.toString(),
            RankingKey.TTL_SECONDS.toString(),
        ) == APPLIED
    }

    private fun <T> execute(action: () -> T): T = try {
        action()
    } catch (e: RedisException) {
        throw DataAccessResourceFailureException(OPERATION_FAILED_MESSAGE, e)
    }

    companion object {
        private const val APPLIED = 1L
        private const val OPERATION_FAILED_MESSAGE = "랭킹 Redis 연산에 실패했습니다."
    }
}
