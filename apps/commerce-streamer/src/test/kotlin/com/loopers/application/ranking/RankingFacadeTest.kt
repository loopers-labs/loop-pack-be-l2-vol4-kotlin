package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingSignal
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
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

    @DisplayName("이월을 실행하면,")
    @Nested
    inner class CarryOverToTomorrow {
        @Test
        fun `오늘 판을 소스로, 내일 판을 목적지로, 이월 가중치와 보존 기간을 저장소에 위임한다`() {
            facade.carryOverToTomorrow(LocalDate.of(2026, 7, 14))

            verify { rankingRepository.carryOver("rank:all:20260714", "rank:all:20260715", 0.1, 172_800L) }
        }
    }

    @DisplayName("상품 삭제를 반영하면,")
    @Nested
    inner class RemoveProduct {
        @Test
        fun `보존 기간 안의 모든 일간 랭킹판에서 그 상품을 제거한다`() {
            // ttl 48h 기준: 이월로 미리 생성된 내일 판 ~ 보존 범위 과거까지 훑는다(하드코딩 '오늘·어제' 아님).
            facade.removeProduct(productId = 101L, occurredAt = occurredAt)

            verify {
                rankingRepository.removeProduct(
                    listOf("rank:all:20260715", "rank:all:20260714", "rank:all:20260713", "rank:all:20260712"),
                    101L,
                )
            }
        }
    }
}
