package com.loopers.domain.ranking.infrastructure.redis

import com.loopers.domain.ranking.port.ProductRankingReader
import com.loopers.domain.ranking.vo.RankingKey
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RedissonProductRankingReader(
    private val redissonClient: RedissonClient,
) : ProductRankingReader {
    override fun findProductIds(
        date: LocalDate,
        page: Int,
        size: Int,
    ): List<Long> = execute {
        val offset = page * size
        rankingSet(date)
            .valueRangeReversed(offset, offset + size - 1)
            .map { it.toLong() }
    }

    override fun count(date: LocalDate): Long = execute {
        rankingSet(date).size().toLong()
    }

    override fun findRank(
        date: LocalDate,
        productId: Long,
    ): Long? = execute {
        rankingSet(date)
            .revRank(productId.toString())
            ?.toLong()
            ?.plus(1)
    }

    private fun rankingSet(date: LocalDate): RScoredSortedSet<String> =
        redissonClient.getScoredSortedSet(RankingKey.daily(date))

    private fun <T> execute(action: () -> T): T = try {
        action()
    } catch (e: RedisException) {
        throw DataAccessResourceFailureException(OPERATION_FAILED_MESSAGE, e)
    }

    companion object {
        private const val OPERATION_FAILED_MESSAGE = "랭킹 Redis 조회에 실패했습니다."
    }
}
