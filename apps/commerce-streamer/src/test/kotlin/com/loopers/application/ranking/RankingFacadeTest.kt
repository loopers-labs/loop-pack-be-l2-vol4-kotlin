package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingSignal
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RankingFacadeTest {
    private val rankingRepository = mockk<RankingRepository>(relaxed = true)
    private val facade = RankingFacade(
        rankingRepository = rankingRepository,
        properties = RankingProperties(),
    )

    private val occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0)

    @DisplayName("신호를 반영하면,")
    @Nested
    inner class Reflect {
        @Test
        fun `조회 신호는 발생 시각 날짜의 랭킹판에 조회 가중치만큼 누적된다`() {
            val eventId = UUID.randomUUID()

            facade.reflect(eventId, RankingSignal.VIEW, productId = 101L, quantity = 1, occurredAt = occurredAt)

            verify { rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, 0.1, 172_800L) }
        }

        @Test
        fun `좋아요 취소 신호는 좋아요 가중치만큼 차감한다`() {
            val eventId = UUID.randomUUID()

            facade.reflect(eventId, RankingSignal.LIKE_CANCEL, productId = 101L, quantity = 1, occurredAt = occurredAt)

            verify { rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, -0.2, 172_800L) }
        }

        @Test
        fun `주문 신호는 수량 곱하기 주문 가중치만큼 누적한다`() {
            val eventId = UUID.randomUUID()

            facade.reflect(eventId, RankingSignal.ORDER, productId = 101L, quantity = 3, occurredAt = occurredAt)

            verify { rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, 0.7 * 3, 172_800L) }
        }
    }
}
