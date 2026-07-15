package com.loopers.batch.job.ranking.step

import com.fasterxml.jackson.databind.ObjectMapper
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
 * boards KV(ranking:weights:boards)의 모든 적재 버전에 대해 snapshot:{v}:{D}를 페이징 순회하며
 * carry = floor(score × 0.1)을 D+1 보드(all/snapshot)에 반영한다.
 * carry가 0이면 add를 생략해 미미한 점수는 자연 소멸시키고 ZSET 크기를 억제한다.
 *
 * 상태 키(ranking:rollover:status:{v}:{D+1})는 버전별 PROGRESS SET NX로 분산 락을 겸하며 commerce-api의
 * 장애 복구 트리거와 같은 키를 공유한다 — 배치·복구가 서로 중복 진입하지 못한다.
 * 페이지 순회마다 PROGRESS TTL을 갱신(heartbeat)하고, 완료 시 DONE으로 덮어쓴다(락 해제 불필요).
 * 실패 시 PROGRESS를 지우지 않는다 — heartbeat가 멈추므로 최대 10분 내 만료돼 재시도 가능해진다.
 */
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = RankingRolloverJobConfig.JOB_NAME)
@Component
class RankingRolloverTasklet(
    @param:Value("#{jobParameters['requestDate']}") private val requestDate: String?,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) masterTemplate: RedisTemplate<*, *>,
    private val objectMapper: ObjectMapper,
) : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    private val master = masterTemplate as RedisTemplate<String, String>

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val sourceDate = requestDate?.let { LocalDate.parse(it) } ?: LocalDate.now(ZONE)
        val targetDate = sourceDate.plusDays(1)

        boardVersions().forEach { version ->
            val statusKey = "ranking:rollover:status:$version:${targetDate.format(DATE_FORMAT)}"
            val started = master.opsForValue().setIfAbsent(statusKey, STATUS_PROGRESS, PROGRESS_TTL) == true
            if (!started) {
                log.warn("이월 상태 선점 실패 - 다른 주체가 실행 중이거나 이미 완료됐으므로 이 버전은 건너뛴다. statusKey={}", statusKey)
                return@forEach
            }

            carryOver(version, sourceDate, targetDate, statusKey)
            master.opsForValue().set(statusKey, STATUS_DONE, DONE_TTL)
        }
        return RepeatStatus.FINISHED
    }

    /** 적재 대상 버전 목록. KV 미존재/장애 시 기본 v1 — 이월이 설정 조회 장애로 멈추면 안 된다. */
    private fun boardVersions(): List<String> = runCatching {
        val json = master.opsForValue().get(BOARDS_KEY) ?: return listOf(DEFAULT_VERSION)
        objectMapper.readTree(json).mapNotNull { it.get("version")?.asText() }.ifEmpty { listOf(DEFAULT_VERSION) }
    }.getOrElse {
        log.warn("가중치 boards KV 조회 실패 - 기본 {}만 이월한다.", DEFAULT_VERSION, it)
        listOf(DEFAULT_VERSION)
    }

    private fun carryOver(version: String, sourceDate: LocalDate, targetDate: LocalDate, statusKey: String) {
        val fromKey = "ranking:snapshot:$version:${sourceDate.format(DATE_FORMAT)}"
        val toAllKey = "ranking:all:$version:${targetDate.format(DATE_FORMAT)}"
        val toSnapshotKey = "ranking:snapshot:$version:${targetDate.format(DATE_FORMAT)}"

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

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
        private const val BOARDS_KEY = "ranking:weights:boards"
        private const val DEFAULT_VERSION = "v1"
        private const val STATUS_PROGRESS = "PROGRESS"
        private const val STATUS_DONE = "DONE"
        private val PROGRESS_TTL = Duration.ofMinutes(10)
        private val DONE_TTL = Duration.ofDays(2)
        private val ZSET_TTL = Duration.ofDays(2)
        private const val CARRY_OVER_FACTOR = 0.1
        private const val PAGE_SIZE = 1000
    }
}
