package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingBoard
import com.loopers.domain.ranking.RankingRolloverPort
import com.loopers.domain.ranking.RankingRolloverStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor

/**
 * 이월 상태 머신 어댑터. ranking:rollover:status:{v}:{D}가 PROGRESS SET NX로 분산 락을 겸하고,
 * 완료 시 DONE으로 덮어써 별도 락 해제가 필요 없다. 정기 이월 배치(commerce-batch)와 같은 키를 공유해
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

    override fun getStatus(version: String, targetDate: LocalDate): RankingRolloverStatus =
        when (master.opsForValue().get(statusKey(version, targetDate))) {
            STATUS_DONE -> RankingRolloverStatus.DONE
            STATUS_PROGRESS -> RankingRolloverStatus.IN_PROGRESS
            else -> RankingRolloverStatus.NOT_STARTED
        }

    override fun tryStart(version: String, targetDate: LocalDate): Boolean =
        master.opsForValue().setIfAbsent(statusKey(version, targetDate), STATUS_PROGRESS, PROGRESS_TTL) == true

    override fun carryOverSnapshot(version: String, fromDate: LocalDate, toDate: LocalDate) {
        val fromKey = RankingBoard.snapshotOf(version, fromDate).key()
        val toAllKey = RankingBoard.allOf(version, toDate).key()
        val toSnapshotKey = RankingBoard.snapshotOf(version, toDate).key()
        val statusKey = statusKey(version, toDate)

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

            // heartbeat - 살아있는 한 총 소요가 PROGRESS TTL을 넘어도 상태가 유지되고, 크래시하면 갱신이 멈춰 자연 만료된다
            master.expire(statusKey, PROGRESS_TTL)

            if (tuples.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }

        master.expire(toAllKey, ZSET_TTL)
        master.expire(toSnapshotKey, ZSET_TTL)
        log.info("랭킹 이월 완료. from={}, to={}, carriedMembers={}", fromKey, toAllKey, carried)
    }

    override fun complete(version: String, targetDate: LocalDate) {
        master.opsForValue().set(statusKey(version, targetDate), STATUS_DONE, DONE_TTL)
    }

    override fun tryMarkNotified(version: String, targetDate: LocalDate): Boolean =
        master.opsForValue().setIfAbsent(notifiedKey(version, targetDate), "1", NOTIFIED_TTL) == true

    private fun statusKey(version: String, targetDate: LocalDate): String =
        "ranking:rollover:status:$version:${targetDate.format(DateTimeFormatter.BASIC_ISO_DATE)}"

    private fun notifiedKey(version: String, targetDate: LocalDate): String =
        "ranking:rollover:notified:$version:${targetDate.format(DateTimeFormatter.BASIC_ISO_DATE)}"

    companion object {
        private const val STATUS_PROGRESS = "PROGRESS"
        private const val STATUS_DONE = "DONE"
        private val PROGRESS_TTL = Duration.ofMinutes(10)
        private val DONE_TTL = Duration.ofDays(2)
        private val NOTIFIED_TTL = Duration.ofDays(1)
        private val ZSET_TTL = Duration.ofDays(2)
        private const val CARRY_OVER_FACTOR = 0.1
        private const val PAGE_SIZE = 1000
    }
}
