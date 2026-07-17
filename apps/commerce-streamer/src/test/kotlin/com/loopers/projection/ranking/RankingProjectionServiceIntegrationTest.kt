package com.loopers.projection.ranking

import com.loopers.projection.ranking.application.RankingKey
import com.loopers.projection.ranking.application.RankingProjectionCommand
import com.loopers.projection.ranking.application.RankingProjectionService
import com.loopers.projection.ranking.application.RankingScoreDelta
import com.loopers.testcontainers.RedisTestContainerInitializer
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [RedisTestContainerInitializer::class])
class RankingProjectionServiceIntegrationTest
    @Autowired
    constructor(
        private val rankingProjectionService: RankingProjectionService,
        private val redissonClient: RedissonClient,
    ) {
        @AfterEach
        fun tearDown() {
            redissonClient.keys.flushdb()
        }

        @Test
        fun `이벤트_deltas_를_발생일자_랭킹판에_누적한다`() {
            rankingProjectionService.project(
                command(
                    deltas = listOf(
                        RankingScoreDelta(productId = 1L, score = 1.0),
                        RankingScoreDelta(productId = 2L, score = 4.0),
                    ),
                ),
            )
            rankingProjectionService.project(
                command(deltas = listOf(RankingScoreDelta(productId = 1L, score = -1.0))),
            )

            assertThat(score(1L)).isEqualTo(0.0)
            assertThat(score(2L)).isEqualTo(4.0)
        }

        @Test
        fun `동일_이벤트를_재처리해도_점수는_한_번만_반영된다`() {
            val eventId = UUID.randomUUID()
            val command = command(
                eventId = eventId,
                deltas = listOf(RankingScoreDelta(productId = 1L, score = 4.0)),
            )

            rankingProjectionService.project(command)
            rankingProjectionService.project(command)

            assertThat(score(1L)).isEqualTo(4.0)
        }

        @Test
        fun `발생_시각의_서울_날짜_기준으로_랭킹판을_선택한다`() {
            rankingProjectionService.project(
                command(
                    occurredAt = ZonedDateTime.parse("2026-07-17T23:30:00Z"),
                    deltas = listOf(RankingScoreDelta(productId = 1L, score = 1.0)),
                ),
            )

            val nextDayKey = RankingKey.daily(LocalDate.of(2026, 7, 18))
            assertThat(redissonClient.getScoredSortedSet<String>(nextDayKey).getScore("1")).isEqualTo(1.0)
        }

        private fun command(
            eventId: UUID = UUID.randomUUID(),
            occurredAt: ZonedDateTime = 발생_시각,
            deltas: List<RankingScoreDelta>,
        ): RankingProjectionCommand = RankingProjectionCommand(
            eventId = eventId,
            occurredAt = occurredAt,
            deltas = deltas,
        )

        private fun score(productId: Long): Double? =
            redissonClient.getScoredSortedSet<String>(RankingKey.daily(날짜)).getScore(productId.toString())

        companion object {
            private val 발생_시각 = ZonedDateTime.parse("2026-07-17T10:00:00+09:00[Asia/Seoul]")
            private val 날짜 = LocalDate.of(2026, 7, 17)
        }
    }
