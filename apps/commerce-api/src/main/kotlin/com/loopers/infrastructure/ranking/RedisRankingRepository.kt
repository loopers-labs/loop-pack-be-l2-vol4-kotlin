package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun findPage(
        period: RankingPeriod,
        date: LocalDate,
        pageable: Pageable,
    ): RankingPage {
        val key = period.key(date)
        val zset = redisTemplate.opsForZSet()

        val total = zset.size(key) ?: 0L
        if (total == 0L) return RankingPage(emptyList(), 0L)

        val start = pageable.offset
        val end = start + pageable.pageSize - 1
        val members = zset.reverseRange(key, start, end) ?: emptySet()

        val entries = members.mapIndexed { index, member ->
            RankingEntry(productId = member.toLong(), rank = start + index + 1)
        }
        return RankingPage(entries, total)
    }

    override fun findRank(period: RankingPeriod, date: LocalDate, productId: Long): Long? {
        val rank = redisTemplate.opsForZSet().reverseRank(period.key(date), productId.toString())
        return rank?.plus(1)
    }
}