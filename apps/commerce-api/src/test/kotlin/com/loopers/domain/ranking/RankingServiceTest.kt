package com.loopers.domain.ranking

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RankingServiceTest {

    private lateinit var rankingRepositoryPort: RankingRepositoryPort
    private lateinit var rankingService: RankingService

    private val date = LocalDate.of(2026, 7, 14)
    private val board = RankingBoard.allOf(date)

    @BeforeEach
    fun setUp() {
        rankingRepositoryPort = mockk()
        rankingService = RankingService(rankingRepositoryPort)
    }

    @DisplayName("페이지 조회 시 offset=(page-1)*size로 환산해 조회한다.")
    @Test
    fun calculatesOffset_fromPageAndSize() {
        every { rankingRepositoryPort.getPage(board, 40L, 20L) } returns emptyList()
        every { rankingRepositoryPort.getTotalCount(board) } returns 0L

        rankingService.getPage(date, page = 3, size = 20)

        verify(exactly = 1) { rankingRepositoryPort.getPage(board, 40L, 20L) }
    }

    @DisplayName("조회 결과와 totalCount를 RankingPage로 묶어 반환한다.")
    @Test
    fun composesRankingPage() {
        val entries = listOf(
            RankingEntry(productId = 101L, score = 1280.0, rank = 1L),
            RankingEntry(productId = 102L, score = 500.0, rank = 2L),
        )
        every { rankingRepositoryPort.getPage(board, 0L, 20L) } returns entries
        every { rankingRepositoryPort.getTotalCount(board) } returns 134L

        val result = rankingService.getPage(date, page = 1, size = 20)

        assertThat(result.date).isEqualTo(date)
        assertThat(result.totalCount).isEqualTo(134L)
        assertThat(result.entries).isEqualTo(entries)
    }

    @DisplayName("상품 랭킹 조회 시 보드에 없으면 null을 반환한다.")
    @Test
    fun returnsNull_whenProductNotRanked() {
        every { rankingRepositoryPort.getEntry(board, 999L) } returns null

        assertThat(rankingService.getProductRanking(date, 999L)).isNull()
    }

    @DisplayName("상품 랭킹 조회 시 보드에 있으면 rank/score를 반환한다.")
    @Test
    fun returnsEntry_whenProductRanked() {
        val entry = RankingEntry(productId = 101L, score = 1280.0, rank = 3L)
        every { rankingRepositoryPort.getEntry(board, 101L) } returns entry

        assertThat(rankingService.getProductRanking(date, 101L)).isEqualTo(entry)
    }

    @DisplayName("exists는 all 보드 키 존재 여부를 반환한다.")
    @Test
    fun delegatesExists() {
        every { rankingRepositoryPort.exists(board) } returns false

        assertThat(rankingService.exists(date)).isFalse()
    }
}
