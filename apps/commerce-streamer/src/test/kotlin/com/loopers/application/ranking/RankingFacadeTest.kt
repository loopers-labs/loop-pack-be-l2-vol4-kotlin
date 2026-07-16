package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingSignal
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RankingFacadeTest {
    private val rankingRepository = mockk<RankingRepository>(relaxed = true)
    private val facade = RankingFacade(
        rankingRepository = rankingRepository,
        properties = RankingProperties(),
    )

    @Test
    fun `조회 신호는 발생 시각 날짜의 랭킹판에 조회 가중치만큼 누적된다`() {
        val eventId = UUID.randomUUID()

        facade.reflect(
            eventId = eventId,
            signal = RankingSignal.VIEW,
            productId = 101L,
            quantity = 1,
            occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0),
        )

        verify {
            rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, 0.1, 172_800L)
        }
    }
}
