package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingRolloverJobConfig
import com.loopers.config.redis.RedisConfig
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor

/**
 * snapshot:{D}를 페이징 순회하며 carry = floor(score × 0.1)을 D+1 보드(all/snapshot)에 반영한다.
 * carry가 0이면 add를 생략해 미미한 점수는 자연 소멸시키고 ZSET 크기를 억제한다.
 *
 * 분산 락(ranking:rollover:lock:{D+1})은 commerce-api의 장애 복구 트리거와 같은 키를 공유해
 * 배치·복구가 서로 중복 진입하지 못하게 한다.
 */
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = RankingRolloverJobConfig.JOB_NAME)
@Component
class RankingRolloverTasklet(
    @param:Value("#{jobParameters['requestDate']}") private val requestDate: String?,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) masterTemplate: RedisTemplate<*, *>,
) : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    private val master = masterTemplate as RedisTemplate<String, String>

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val sourceDate = requestDate?.let { LocalDate.parse(it) } ?: LocalDate.now(ZONE)
        val targetDate = sourceDate.plusDays(1)
        val lockKey = "ranking:rollover:lock:${targetDate.format(DATE_FORMAT)}"

        val locked = master.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL) == true
        if (!locked) {
            log.warn("이월 락 획득 실패 - 다른 인스턴스가 실행 중이므로 종료한다. lockKey={}", lockKey)
            return RepeatStatus.FINISHED
        }

        try {
            carryOver(sourceDate, targetDate)
        } finally {
            master.delete(lockKey)
        }
        return RepeatStatus.FINISHED
    }

    private fun carryOver(sourceDate: LocalDate, targetDate: LocalDate) {
        val fromKey = "ranking:snapshot:${sourceDate.format(DATE_FORMAT)}"
        val toAllKey = "ranking:all:${targetDate.format(DATE_FORMAT)}"
        val toSnapshotKey = "ranking:snapshot:${targetDate.format(DATE_FORMAT)}"

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

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
        private val LOCK_TTL = Duration.ofMinutes(5)
        private val ZSET_TTL = Duration.ofDays(2)
        private const val CARRY_OVER_FACTOR = 0.1
        private const val PAGE_SIZE = 1000
    }
}
