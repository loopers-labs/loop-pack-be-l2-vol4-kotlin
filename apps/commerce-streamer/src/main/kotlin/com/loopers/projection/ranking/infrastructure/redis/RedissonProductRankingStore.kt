package com.loopers.projection.ranking.infrastructure.redis

import com.loopers.projection.ranking.application.RankingEntry
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

    override fun carryOver(
        from: LocalDate,
        to: LocalDate,
        decay: Double,
        minScore: Double,
    ): Boolean = execute {
        redissonClient.script.eval<Long>(
            RScript.Mode.READ_WRITE,
            RankingRedisScripts.CARRY_OVER,
            RScript.ReturnType.INTEGER,
            listOf(RankingKey.carryOverMarker(to), RankingKey.daily(from), RankingKey.daily(to)),
            decay.toString(),
            minScore.toString(),
            RankingKey.TTL_SECONDS.toString(),
        ) == APPLIED
    }

    override fun topEntries(
        date: LocalDate,
        limit: Int,
    ): List<RankingEntry> = execute {
        redissonClient.getScoredSortedSet<String>(RankingKey.daily(date))
            .entryRangeReversed(0, limit - 1)
            .map { RankingEntry(productId = it.value.toLong(), score = it.score) }
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
