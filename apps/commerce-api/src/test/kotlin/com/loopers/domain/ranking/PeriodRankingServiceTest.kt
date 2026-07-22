package com.loopers.domain.ranking

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodRankingServiceTest {

    private lateinit var periodRankingRepositoryPort: PeriodRankingRepositoryPort
    private lateinit var periodRankingService: PeriodRankingService

    @BeforeEach
    fun setUp() {
        periodRankingRepositoryPort = mockk()
        periodRankingService = PeriodRankingService(periodRankingRepositoryPort)
    }

    @DisplayName("기준일을 직전 완결 주의 시작일로 변환해 MV를 조회하고, RankingPage로 조립한다.")
    @Test
    fun resolvesAggregatedDate_andAssemblesPage() {
        val date = LocalDate.of(2026, 7, 22) // 수요일 → 지난주 시작 7/13
        val aggregatedDate = LocalDate.of(2026, 7, 13)
        val entries = listOf(RankingEntry(productId = 10L, score = 1000.0, rank = 1L))
        every { periodRankingRepositoryPort.getPage(RankingPeriod.WEEKLY, aggregatedDate, 1, 20) } returns entries
        every { periodRankingRepositoryPort.getTotalCount(RankingPeriod.WEEKLY, aggregatedDate) } returns 1L

        val page = periodRankingService.getPage(RankingPeriod.WEEKLY, date, 1, 20)

        assertThat(page.date).isEqualTo(date)
        assertThat(page.page).isEqualTo(1)
        assertThat(page.size).isEqualTo(20)
        assertThat(page.totalCount).isEqualTo(1L)
        assertThat(page.entries).isEqualTo(entries)
        verify(exactly = 1) { periodRankingRepositoryPort.getPage(RankingPeriod.WEEKLY, aggregatedDate, 1, 20) }
    }

    @DisplayName("MV 스냅샷이 없으면(배치 미실행) 빈 페이지를 반환한다 — 다른 기간으로 폴백하지 않는다.")
    @Test
    fun returnsEmptyPage_whenSnapshotMissing() {
        val date = LocalDate.of(2026, 7, 20)
        every { periodRankingRepositoryPort.getPage(any(), any(), any(), any()) } returns emptyList()
        every { periodRankingRepositoryPort.getTotalCount(any(), any()) } returns 0L

        val page = periodRankingService.getPage(RankingPeriod.MONTHLY, date, 1, 20)

        assertThat(page.entries).isEmpty()
        assertThat(page.totalCount).isEqualTo(0L)
    }
}
