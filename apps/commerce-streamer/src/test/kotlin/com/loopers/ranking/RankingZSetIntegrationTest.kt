package com.loopers.ranking

import com.loopers.ranking.application.RankingAccumulateService
import com.loopers.ranking.application.ScoreChange
import com.loopers.ranking.domain.RankingKeys
import com.loopers.ranking.domain.RankingWeights
import com.loopers.config.redis.RedisConfig
import com.loopers.ranking.infrastructure.RankingZSetRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@ActiveProfiles("test")
class RankingZSetIntegrationTest @Autowired constructor(
    private val rankingZSetRepository: RankingZSetRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @BeforeEach
    fun setUp() {
        redisCleanUp.truncateAll()
    }

    private fun serviceAt(instant: Instant) =
        RankingAccumulateService(rankingZSetRepository, Clock.fixed(instant, KST))

    private fun score(key: String, productId: Long): Double? =
        redisTemplate.opsForZSet().score(key, productId.toString())

    @DisplayName("신규 이벤트를 소비하면 오늘 키에 가중치를 적재하고 멱등 키를 남긴다 — carry 는 per-event 가 아니다.")
    @Test
    fun accumulatesTodayScoreAndMarksHandled() {
        serviceAt(NOON).accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.VIEW)))

        assertAll(
            { assertThat(score(TODAY_KEY, 1)!!).isCloseTo(0.1, EPS) },
            { assertThat(redisTemplate.hasKey("ranking:handled:e-1")).isTrue() },
            { assertThat(score(TOMORROW_KEY, 1)).isNull() },
        )
    }

    @DisplayName("같은 eventId 를 다시 소비하면 점수를 반복 적재하지 않는다 — Lua SET NX 멱등.")
    @Test
    fun skipsAccumulation_whenDuplicateEventId() {
        val service = serviceAt(NOON)
        service.accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.LIKE)))
        service.accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.LIKE)))

        assertThat(score(TODAY_KEY, 1)!!).isCloseTo(0.2, EPS)
    }

    @DisplayName("좋아요 취소(-0.2)가 단독으로 오면 음수 score 를 허용한다.")
    @Test
    fun allowsNegativeScore_whenUnlikedAlone() {
        serviceAt(NOON).accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.LIKE.negate())))

        assertThat(score(TODAY_KEY, 1)!!).isCloseTo(-0.2, EPS)
    }

    @DisplayName("한 주문 이벤트의 여러 라인은 각 상품에 order line 당 +0.7 로 전개된다 — ARGV 펼침.")
    @Test
    fun expandsEachOrderLine() {
        serviceAt(NOON).accumulate(
            "e-1",
            NOON,
            listOf(ScoreChange(1, RankingWeights.ORDER_LINE), ScoreChange(2, RankingWeights.ORDER_LINE)),
        )

        assertAll(
            { assertThat(score(TODAY_KEY, 1)!!).isCloseTo(0.7, EPS) },
            { assertThat(score(TODAY_KEY, 2)!!).isCloseTo(0.7, EPS) },
        )
    }

    @DisplayName("주문 1건(0.7) 이 좋아요 3건(0.6) 보다 높은 점수를 가진다 — 가중치 요건.")
    @Test
    fun singleOrderOutweighsThreeLikes() {
        val service = serviceAt(NOON)
        service.accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.ORDER_LINE)))
        service.accumulate("e-2", NOON, listOf(ScoreChange(2, RankingWeights.LIKE)))
        service.accumulate("e-3", NOON, listOf(ScoreChange(2, RankingWeights.LIKE)))
        service.accumulate("e-4", NOON, listOf(ScoreChange(2, RankingWeights.LIKE)))

        assertThat(score(TODAY_KEY, 1)!!).isGreaterThan(score(TODAY_KEY, 2)!!)
    }

    @DisplayName("발생 시각을 KST 로 환산해 키를 가른다 — KST 23:59 발생은 오늘 키, 다음날 00:00 발생은 내일 키.")
    @Test
    fun splitsKeyByKstDate() {
        val todayLate = TODAY.atTime(23, 59).atZone(KST).toInstant()
        val tomorrowStart = TODAY.plusDays(1).atStartOfDay(KST).toInstant()
        serviceAt(NOON).accumulate("e-1", todayLate, listOf(ScoreChange(1, RankingWeights.VIEW)))
        serviceAt(NOON).accumulate("e-2", tomorrowStart, listOf(ScoreChange(2, RankingWeights.VIEW)))

        assertAll(
            { assertThat(score(TODAY_KEY, 1)!!).isCloseTo(0.1, EPS) },
            { assertThat(score(TOMORROW_KEY, 2)!!).isCloseTo(0.1, EPS) },
        )
    }

    @DisplayName("벽시계 23:50~00:00 소비분만 tail 키에 추가 적재하고, 그 외 시간엔 tail 을 만들지 않는다.")
    @Test
    fun recordsTailOnlyWithinGraceWindow() {
        val at2355 = TODAY.atTime(23, 55).atZone(KST).toInstant()
        serviceAt(at2355).accumulate("e-1", at2355, listOf(ScoreChange(1, RankingWeights.VIEW)))
        serviceAt(NOON).accumulate("e-2", NOON, listOf(ScoreChange(2, RankingWeights.VIEW)))

        assertAll(
            { assertThat(score(TODAY_KEY, 1)!!).isCloseTo(0.1, EPS) },
            { assertThat(score(TAIL_KEY, 1)!!).isCloseTo(0.1, EPS) },
            { assertThat(score(TAIL_KEY, 2)).isNull() },
        )
    }

    @DisplayName("23:50 스냅샷은 내일 키를 오늘×0.1 로 pre-warm 한다.")
    @Test
    fun snapshotPreWarmsNextDayByTenPercent() {
        serviceAt(NOON).accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.ORDER_LINE)))

        rankingZSetRepository.snapshotToNextDay(TODAY)

        assertThat(score(TOMORROW_KEY, 1)!!).isCloseTo(0.07, EPS)
    }

    @DisplayName("00:00 tail 병합은 스냅샷 이후 증분을 ×0.1 로 더하고, 재실행 시 SET NX 가드로 이중 가산하지 않는다.")
    @Test
    fun mergesTailOnceAndGuardsReRun() {
        serviceAt(NOON).accumulate("e-1", NOON, listOf(ScoreChange(1, RankingWeights.ORDER_LINE)))
        rankingZSetRepository.snapshotToNextDay(TODAY)

        val at2355 = TODAY.atTime(23, 55).atZone(KST).toInstant()
        serviceAt(at2355).accumulate("e-2", at2355, listOf(ScoreChange(1, RankingWeights.VIEW)))

        val first = rankingZSetRepository.mergeTailIntoNextDay(TODAY)
        val second = rankingZSetRepository.mergeTailIntoNextDay(TODAY)

        assertAll(
            { assertThat(first).isTrue() },
            { assertThat(second).isFalse() },
            { assertThat(score(TOMORROW_KEY, 1)!!).isCloseTo(0.08, EPS) },
            { assertThat(redisTemplate.hasKey(TAIL_KEY)).isFalse() },
        )
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")

        // 키 TTL 이 "키 날짜 +2일 자정" EXPIREAT 라, 과거 날짜로 고정하면 키가 즉시 만료된다 — 항상 실행일 기준
        val TODAY: LocalDate = LocalDate.now(KST)
        val NOON: Instant = TODAY.atTime(12, 0).atZone(KST).toInstant()
        val TODAY_KEY = RankingKeys.today(TODAY)
        val TOMORROW_KEY = RankingKeys.today(TODAY.plusDays(1))
        val TAIL_KEY = RankingKeys.tail(TODAY)
        val EPS = within(1e-9)
    }
}
