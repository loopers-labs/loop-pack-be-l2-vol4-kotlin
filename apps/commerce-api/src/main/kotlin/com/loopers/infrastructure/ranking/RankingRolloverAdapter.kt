package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingBoard
import com.loopers.domain.ranking.RankingRolloverPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor

/**
 * 이월 복구 실행 어댑터 (쓰기이므로 마스터 템플릿). 락 키는 정기 이월 배치(commerce-batch)와 공유해
 * 배치 실행 중 api 측 복구가 중복 진입하지 못하게 한다.
 */
@Component
class RankingRolloverAdapter(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    masterTemplate: RedisTemplate<*, *>,
) : RankingRolloverPort {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    private val master = masterTemplate as RedisTemplate<String, String>

    override fun tryLock(targetDate: LocalDate): Boolean =
        master.opsForValue().setIfAbsent(lockKey(targetDate), "1", LOCK_TTL) == true

    override fun releaseLock(targetDate: LocalDate) {
        master.delete(lockKey(targetDate))
    }

    override fun carryOverSnapshot(fromDate: LocalDate, toDate: LocalDate) {
        val fromKey = RankingBoard.snapshotOf(fromDate).key()
        val toAllKey = RankingBoard.allOf(toDate).key()
        val toSnapshotKey = RankingBoard.snapshotOf(toDate).key()

        var offset = 0L
        var carried = 0L
        while (true) {
            val tuples = master.opsForZSet()
                .rangeWithScores(fromKey, offset, offset + PAGE_SIZE - 1)
                ?.takeIf { it.isNotEmpty() }
                ?: break

            tuples.forEach { tuple ->
                val member = tuple.value ?: return@forEach
                val carry = floor((tuple.score ?: 0.0) * CARRY_OVER_FACTOR).toLong()
                if (carry == 0L) return@forEach
                master.opsForZSet().incrementScore(toAllKey, member, carry.toDouble())
                master.opsForZSet().incrementScore(toSnapshotKey, member, carry.toDouble())
                carried++
            }

            if (tuples.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }

        master.expire(toAllKey, ZSET_TTL)
        master.expire(toSnapshotKey, ZSET_TTL)
        log.info("랭킹 이월 완료. from={}, to={}, carriedMembers={}", fromKey, toAllKey, carried)
    }

    private fun lockKey(targetDate: LocalDate): String =
        "ranking:rollover:lock:${targetDate.format(DateTimeFormatter.BASIC_ISO_DATE)}"

    companion object {
        private val LOCK_TTL = Duration.ofMinutes(5)
        private val ZSET_TTL = Duration.ofDays(2)
        private const val CARRY_OVER_FACTOR = 0.1
        private const val PAGE_SIZE = 1000
    }
}
