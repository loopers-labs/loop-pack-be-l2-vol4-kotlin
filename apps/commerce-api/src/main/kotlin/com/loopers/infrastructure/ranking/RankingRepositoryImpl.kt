package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankedProductId
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingRepositoryImpl(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val weeklyRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyRepository: MvProductRankMonthlyJpaRepository,
) : RankingRepository {
    override fun findTopN(date: LocalDate, offset: Long, count: Long): List<RankedProductId> {
        require(offset >= 0) { "offset은 0 이상이어야 합니다. offset=$offset" }
        require(count >= 0) { "count는 0 이상이어야 합니다. count=$count" }
        if (count == 0L) return emptyList()

        val members = redisTemplate.opsForZSet()
            .reverseRange(RankingKeyGenerator.daily(date), offset, offset + count - 1)
            ?: return emptyList()

        return members.mapIndexed { index, member ->
            RankedProductId(
                productId = member.toLong(),
                rank = offset + index + 1,
            )
        }
    }

    override fun countByDate(date: LocalDate): Long {
        return redisTemplate.opsForZSet().zCard(RankingKeyGenerator.daily(date)) ?: 0L
    }

    override fun findRank(date: LocalDate, productId: Long): Long? {
        val rank = redisTemplate.opsForZSet()
            .reverseRank(RankingKeyGenerator.daily(date), productId.toString())
            ?: return null
        return rank + 1
    }

    override fun findWeeklyTopN(periodStart: LocalDate, offset: Long, count: Long): List<RankedProductId> {
        val pageable = PageRequest.of((offset / count).toInt(), count.toInt())
        return weeklyRepository.findByPeriodStartOrderByRankAsc(periodStart, pageable)
            .map { RankedProductId(productId = it.productId, rank = it.rank.toLong()) }
    }

    override fun countWeekly(periodStart: LocalDate): Long =
        weeklyRepository.countByPeriodStart(periodStart)

    override fun findMonthlyTopN(periodStart: LocalDate, offset: Long, count: Long): List<RankedProductId> {
        val pageable = PageRequest.of((offset / count).toInt(), count.toInt())
        return monthlyRepository.findByPeriodStartOrderByRankAsc(periodStart, pageable)
            .map { RankedProductId(productId = it.productId, rank = it.rank.toLong()) }
    }

    override fun countMonthly(periodStart: LocalDate): Long =
        monthlyRepository.countByPeriodStart(periodStart)
}
