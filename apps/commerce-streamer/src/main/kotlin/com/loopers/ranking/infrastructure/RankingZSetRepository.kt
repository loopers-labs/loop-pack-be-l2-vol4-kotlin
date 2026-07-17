package com.loopers.ranking.infrastructure

import com.loopers.config.redis.RedisConfig
import com.loopers.ranking.domain.RankingKeys
import com.loopers.ranking.domain.ScoreChange
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.connection.zset.Aggregate
import org.springframework.data.redis.connection.zset.Weights
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDate

@Repository
class RankingZSetRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
) {
    fun accumulate(
        eventId: String,
        eventDate: LocalDate,
        recordTail: Boolean,
        changes: List<ScoreChange>,
    ): Boolean {
        val args = ArrayList<String>(4 + changes.size * 2).apply {
            add(HANDLED_TTL_SECONDS.toString())
            add(RankingKeys.expireAtEpochSecond(eventDate).toString())
            add(RankingKeys.expireAtEpochSecond(eventDate).toString())
            add(if (recordTail) "1" else "0")
            changes.forEach { change ->
                add(change.productId.toString())
                add(change.amount.toPlainString())
            }
        }
        val applied = redisTemplate.execute(
            ACCUMULATE_SCRIPT,
            listOf(RankingKeys.today(eventDate), RankingKeys.tail(eventDate), handledKey(eventId)),
            *args.toTypedArray(),
        )
        return applied == 1L
    }

    fun snapshotToNextDay(date: LocalDate) {
        redisTemplate.opsForZSet().unionAndStore(
            RankingKeys.today(date),
            emptyList(),
            RankingKeys.today(date.plusDays(1)),
            Aggregate.SUM,
            Weights.of(RankingKeys.CARRY_WEIGHT),
        )
    }

    fun mergeTailIntoNextDay(date: LocalDate): Boolean {
        val nextDay = date.plusDays(1)
        val firstMerge = redisTemplate.opsForValue()
            .setIfAbsent(RankingKeys.carryMerged(nextDay), "1", CARRY_MERGED_TTL) ?: false
        if (!firstMerge) {
            return false
        }
        redisTemplate.opsForZSet().unionAndStore(
            RankingKeys.today(nextDay),
            listOf(RankingKeys.tail(date)),
            RankingKeys.today(nextDay),
            Aggregate.SUM,
            Weights.of(1.0, RankingKeys.CARRY_WEIGHT),
        )
        redisTemplate.delete(RankingKeys.tail(date))
        return true
    }

    private fun handledKey(eventId: String): String = "$HANDLED_KEY_PREFIX$eventId"

    companion object {
        const val HANDLED_KEY_PREFIX = "ranking:handled:"
        private const val HANDLED_TTL_SECONDS = 172_800L
        private val CARRY_MERGED_TTL: Duration = Duration.ofDays(2)

        private val ACCUMULATE_SCRIPT = DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("ranking-accumulate.lua"))
            setResultType(Long::class.java)
        }
    }
}
