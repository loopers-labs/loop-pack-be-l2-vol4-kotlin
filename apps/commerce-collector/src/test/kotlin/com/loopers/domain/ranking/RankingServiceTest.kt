package com.loopers.domain.ranking

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class RankingServiceTest {

    private lateinit var rankingRepositoryPort: RankingRepositoryPort
    private lateinit var rankingEventInboxRepositoryPort: RankingEventInboxRepositoryPort
    private lateinit var rankingWeightBoardsPort: RankingWeightBoardsPort
    private lateinit var rankingService: RankingService

    private val date = LocalDate.of(2026, 7, 14)

    @BeforeEach
    fun setUp() {
        rankingRepositoryPort = mockk()
        rankingEventInboxRepositoryPort = mockk()
        rankingWeightBoardsPort = mockk()
        rankingService = RankingService(rankingRepositoryPort, rankingEventInboxRepositoryPort, rankingWeightBoardsPort)

        every { rankingEventInboxRepositoryPort.isHandled(any()) } returns false
        every { rankingEventInboxRepositoryPort.markHandled(any()) } returns Unit
        every { rankingWeightBoardsPort.getActiveBoards() } returns listOf(RankingWeights.default())
        every { rankingRepositoryPort.incrementScore(any(), any(), any(), any()) } returns true
    }

    private fun reflect(occurredAt: ZonedDateTime, type: RankingEventType = RankingEventType.LIKE, delta: Long = 1L) {
        rankingService.reflect(occurredAt = occurredAt, productId = 101L, type = type, delta = delta, eventId = "event-1")
    }

    private fun capturedEntries(version: String = "v1"): List<BoardScore> {
        val entries = slot<List<BoardScore>>()
        verify { rankingRepositoryPort.incrementScore(version, capture(entries), 101L, "event-1") }
        return entries.captured
    }

    @DisplayName("멱등 처리 - ")
    @Nested
    inner class Idempotency {
        @DisplayName("이미 Inbox에 기록된 eventId면, Redis 반영과 Inbox 기록 없이 종료한다.")
        @Test
        fun skips_whenAlreadyHandled() {
            every { rankingEventInboxRepositoryPort.isHandled("event-1") } returns true

            reflect(ZonedDateTime.parse("2026-07-14T10:00:00+09:00[Asia/Seoul]"))

            verify(exactly = 0) { rankingRepositoryPort.incrementScore(any(), any(), any(), any()) }
            verify(exactly = 0) { rankingEventInboxRepositoryPort.markHandled(any()) }
        }

        @DisplayName("Redis dedup에 걸려 incrementScore가 false를 반환해도(재시도 정상 경로), Inbox 기록은 수행된다.")
        @Test
        fun marksHandled_whenRedisDedupSkips() {
            every { rankingRepositoryPort.incrementScore(any(), any(), any(), any()) } returns false

            reflect(ZonedDateTime.parse("2026-07-14T10:00:00+09:00[Asia/Seoul]"))

            verify(exactly = 1) { rankingEventInboxRepositoryPort.markHandled("event-1") }
        }
    }

    @DisplayName("버전별 이중 적재 - ")
    @Nested
    inner class VersionedWrite {
        private val morning = ZonedDateTime.parse("2026-07-14T10:00:00+09:00[Asia/Seoul]")

        @DisplayName("boards에 2개 버전이 있으면 각 버전의 가중치로 버전마다 1회씩 적재된다.")
        @Test
        fun writesEachVersion_withOwnWeights() {
            every { rankingWeightBoardsPort.getActiveBoards() } returns listOf(
                RankingWeights.default(),
                RankingWeights("v2", mapOf(RankingEventType.LIKE to 80L)),
            )

            reflect(morning)

            val v1Entries = capturedEntries("v1")
            val v2Entries = capturedEntries("v2")
            assertThat(v1Entries).containsExactly(
                BoardScore(RankingBoard.allOf("v1", date), 50L),
                BoardScore(RankingBoard.snapshotOf("v1", date), 50L),
            )
            assertThat(v2Entries).containsExactly(
                BoardScore(RankingBoard.allOf("v2", date), 80L),
                BoardScore(RankingBoard.snapshotOf("v2", date), 80L),
            )
        }

        @DisplayName("버전 설정에 없는 이벤트 타입은 기본 가중치로 계산된다.")
        @Test
        fun fallsBackToDefaultWeight_whenTypeMissing() {
            every { rankingWeightBoardsPort.getActiveBoards() } returns listOf(
                RankingWeights("v2", mapOf(RankingEventType.LIKE to 80L)),
            )

            reflect(morning, type = RankingEventType.ORDER)

            val entries = capturedEntries("v2")
            assertThat(entries.map { it.scoreDelta }).containsOnly(500L)
        }
    }

    @DisplayName("컷오프(23:50) 판단 - ")
    @Nested
    inner class Cutoff {
        @DisplayName("23:50 이전 이벤트는 오늘 all/snapshot 2개 보드에 w×delta로 적재된다.")
        @Test
        fun writesTodayBoards_whenBeforeCutoff() {
            reflect(ZonedDateTime.parse("2026-07-14T23:49:59+09:00[Asia/Seoul]"))

            val entries = capturedEntries()
            assertThat(entries).containsExactly(
                BoardScore(RankingBoard.allOf("v1", date), 50L),
                BoardScore(RankingBoard.snapshotOf("v1", date), 50L),
            )
        }

        @DisplayName("정확히 23:50:00 이벤트부터는 이중 적재된다 - 오늘 all + 내일 all/snapshot(0.1배).")
        @Test
        fun writesDoubleBoards_whenExactlyAtCutoff() {
            reflect(ZonedDateTime.parse("2026-07-14T23:50:00+09:00[Asia/Seoul]"))

            val entries = capturedEntries()
            assertThat(entries).containsExactly(
                BoardScore(RankingBoard.allOf("v1", date), 50L),
                BoardScore(RankingBoard.allOf("v1", date.plusDays(1)), 5L),
                BoardScore(RankingBoard.snapshotOf("v1", date.plusDays(1)), 5L),
            )
        }

        @DisplayName("23:59:59 이벤트도 이중 적재 대상이다. snapshot:{오늘}에는 들어가지 않는다(동결).")
        @Test
        fun writesDoubleBoards_whenLateNight() {
            reflect(ZonedDateTime.parse("2026-07-14T23:59:59+09:00[Asia/Seoul]"))

            val entries = capturedEntries()
            assertThat(entries).hasSize(3)
            assertThat(entries.map { it.board }).doesNotContain(RankingBoard.snapshotOf("v1", date))
            assertThat(entries.map { it.board }).contains(RankingBoard.allOf("v1", date.plusDays(1)))
        }

        @DisplayName("occurredAt이 다른 타임존이어도 Asia/Seoul 기준으로 날짜/컷오프를 판단한다.")
        @Test
        fun convertsToSeoulZone_whenDifferentZone() {
            // UTC 14:55 = Seoul 23:55 → 컷오프 이후, 서울 기준 7/14 귀속
            reflect(ZonedDateTime.parse("2026-07-14T14:55:00Z"))

            val entries = capturedEntries()
            assertThat(entries).hasSize(3)
            assertThat(entries[0].board).isEqualTo(RankingBoard.allOf("v1", date))
        }
    }

    @DisplayName("스코어 계산 - ")
    @Nested
    inner class Score {
        private val morning = ZonedDateTime.parse("2026-07-14T10:00:00+09:00[Asia/Seoul]")

        @DisplayName("기본 가중치로 VIEW +1은 +10, LIKE +1은 +50, ORDER +1은 +500으로 적재된다(×10 저장 스케일).")
        @Test
        fun appliesWeightByType() {
            listOf(
                RankingEventType.VIEW to 10L,
                RankingEventType.LIKE to 50L,
                RankingEventType.ORDER to 500L,
            ).forEach { (type, expectedScore) ->
                val entries = slot<List<BoardScore>>()
                every { rankingRepositoryPort.incrementScore(any(), capture(entries), any(), any()) } returns true

                rankingService.reflect(morning, 101L, type, 1L, "event-$type")

                assertThat(entries.captured[0].scoreDelta).isEqualTo(expectedScore)
            }
        }

        @DisplayName("delta가 음수(좋아요 취소)면 점수도 음수로 적재된다.")
        @Test
        fun appliesNegativeScore_whenDeltaNegative() {
            reflect(morning, type = RankingEventType.LIKE, delta = -1L)

            val entries = capturedEntries()
            assertThat(entries.map { it.scoreDelta }).containsOnly(-50L)
        }

        @DisplayName("컷오프 이후 이월분도 delta 부호를 유지한다 (-50 → -5).")
        @Test
        fun keepsSignOnCarryOver_whenDeltaNegative() {
            reflect(ZonedDateTime.parse("2026-07-14T23:55:00+09:00[Asia/Seoul]"), type = RankingEventType.LIKE, delta = -1L)

            val entries = capturedEntries()
            assertThat(entries.map { it.scoreDelta }).containsExactly(-50L, -5L, -5L)
        }
    }
}
