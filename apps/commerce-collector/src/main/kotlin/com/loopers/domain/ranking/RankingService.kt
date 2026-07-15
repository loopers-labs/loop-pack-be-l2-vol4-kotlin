package com.loopers.domain.ranking

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 랭킹 반영 Domain Service. Inbox 멱등 확인 → 적재 대상 버전 결정 → 컷오프 판단(적재 대상 보드 결정) →
 * 버전별 점수 반영 → Inbox 기록을 오케스트레이션한다. 트랜잭션 경계는 application 계층이 가진다.
 *
 * 버전 간 원자성은 필요 없다 — v1과 v2는 독립 보드이고, 한쪽만 반영된 채 실패해도
 * 재시도 시 각 버전의 dedup이 중복을 막는다.
 */
class RankingService(
    private val rankingRepositoryPort: RankingRepositoryPort,
    private val rankingEventInboxRepositoryPort: RankingEventInboxRepositoryPort,
    private val rankingWeightBoardsPort: RankingWeightBoardsPort,
) {
    fun reflect(occurredAt: ZonedDateTime, productId: Long, type: RankingEventType, delta: Long, eventId: String) {
        if (rankingEventInboxRepositoryPort.isHandled(eventId)) return

        rankingWeightBoardsPort.getActiveBoards().forEach { weights ->
            val entries = resolveBoardScores(weights, occurredAt, type, delta)
            rankingRepositoryPort.incrementScore(weights.version, entries, productId, eventId)
        }
        rankingEventInboxRepositoryPort.markHandled(eventId)
    }

    /**
     * 23:50 컷오프 기준 적재 대상 보드 결정.
     * - 00:00 ~ 23:50 : 오늘 all/snapshot += w×delta
     * - 23:50 ~ 24:00 : 오늘 all += w×delta, 내일 all/snapshot += 0.1×w×delta (snapshot:{오늘}은 동결)
     */
    private fun resolveBoardScores(
        weights: RankingWeights,
        occurredAt: ZonedDateTime,
        type: RankingEventType,
        delta: Long,
    ): List<BoardScore> {
        val seoulOccurredAt = occurredAt.withZoneSameInstant(ZONE)
        val date = seoulOccurredAt.toLocalDate()
        val version = weights.version
        val score = weights.weightOf(type) * delta

        return if (seoulOccurredAt.toLocalTime() < CUTOFF) {
            listOf(
                BoardScore(RankingBoard.allOf(version, date), score),
                BoardScore(RankingBoard.snapshotOf(version, date), score),
            )
        } else {
            // 저장 가중치가 ×10 스케일이라 0.1배도 항상 정수다
            val carryScore = score / CARRY_OVER_DIVISOR
            listOf(
                BoardScore(RankingBoard.allOf(version, date), score),
                BoardScore(RankingBoard.allOf(version, date.plusDays(1)), carryScore),
                BoardScore(RankingBoard.snapshotOf(version, date.plusDays(1)), carryScore),
            )
        }
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
        private val CUTOFF = LocalTime.of(23, 50)
        private const val CARRY_OVER_DIVISOR = 10L
    }
}
