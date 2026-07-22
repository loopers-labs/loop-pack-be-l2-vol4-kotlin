package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingPeriod
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class PeriodRankingRepositoryAdapterIntegrationTest @Autowired constructor(
    private val adapter: PeriodRankingRepositoryAdapter,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val aggregatedDate = LocalDate.of(2026, 7, 13)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedWeekly(rankNo: Int, productId: Long, score: Long, date: LocalDate = aggregatedDate) {
        weeklyJpaRepository.save(
            MvProductRankWeeklyEntity(rankNo = rankNo, productId = productId, score = score, aggregatedDate = date),
        )
    }

    @DisplayName("aggregated_date의 스냅샷을 rank_no 오름차순으로 페이징 조회한다.")
    @Test
    fun getsPageOrderedByRank() {
        seedWeekly(rankNo = 2, productId = 20L, score = 500L)
        seedWeekly(rankNo = 1, productId = 10L, score = 1000L)
        seedWeekly(rankNo = 3, productId = 30L, score = 100L)

        val page1 = adapter.getPage(RankingPeriod.WEEKLY, aggregatedDate, page = 1, size = 2)
        val page2 = adapter.getPage(RankingPeriod.WEEKLY, aggregatedDate, page = 2, size = 2)

        assertThat(page1.map { it.productId }).containsExactly(10L, 20L)
        assertThat(page1.first().rank).isEqualTo(1L)
        assertThat(page1.first().score).isEqualTo(1000.0)
        assertThat(page2.map { it.productId }).containsExactly(30L)
        assertThat(adapter.getTotalCount(RankingPeriod.WEEKLY, aggregatedDate)).isEqualTo(3L)
    }

    @DisplayName("다른 aggregated_date의 스냅샷은 조회에 섞이지 않는다.")
    @Test
    fun isolatesSnapshotsByAggregatedDate() {
        seedWeekly(rankNo = 1, productId = 10L, score = 1000L)
        seedWeekly(rankNo = 1, productId = 99L, score = 9999L, date = aggregatedDate.plusWeeks(1))

        val entries = adapter.getPage(RankingPeriod.WEEKLY, aggregatedDate, page = 1, size = 20)

        assertThat(entries.map { it.productId }).containsExactly(10L)
        assertThat(adapter.getTotalCount(RankingPeriod.WEEKLY, aggregatedDate)).isEqualTo(1L)
    }

    @DisplayName("MONTHLY는 월간 테이블에서 조회한다 — 주간 데이터와 분리.")
    @Test
    fun readsMonthlyTable_whenPeriodIsMonthly() {
        val monthStart = LocalDate.of(2026, 6, 1)
        monthlyJpaRepository.save(
            MvProductRankMonthlyEntity(rankNo = 1, productId = 77L, score = 700L, aggregatedDate = monthStart),
        )
        seedWeekly(rankNo = 1, productId = 10L, score = 1000L)

        val entries = adapter.getPage(RankingPeriod.MONTHLY, monthStart, page = 1, size = 20)

        assertThat(entries.map { it.productId }).containsExactly(77L)
    }

    @DisplayName("스냅샷이 없으면 빈 리스트와 totalCount 0을 반환한다.")
    @Test
    fun returnsEmpty_whenNoSnapshot() {
        assertThat(adapter.getPage(RankingPeriod.WEEKLY, aggregatedDate, page = 1, size = 20)).isEmpty()
        assertThat(adapter.getTotalCount(RankingPeriod.WEEKLY, aggregatedDate)).isEqualTo(0L)
    }
}
