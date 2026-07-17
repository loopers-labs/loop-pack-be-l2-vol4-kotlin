package com.loopers.ranking.application

import com.loopers.product.domain.ProductRepository
import com.loopers.ranking.infrastructure.RankingFallbackDaily
import com.loopers.ranking.infrastructure.RankingFallbackDailyJpaRepository
import com.loopers.ranking.infrastructure.RankingZSetReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class RankingQueryServiceFallbackTest {
    private val rankingZSetReader: RankingZSetReader = mock()
    private val rankingFallbackDailyJpaRepository: RankingFallbackDailyJpaRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val service = RankingQueryService(rankingZSetReader, rankingFallbackDailyJpaRepository, productRepository)

    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @DisplayName("Redis 접속 실패(DataAccessException)면 페이지 조회가 MySQL fallback 판으로 전환된다.")
    @Test
    fun fallsBackToMysqlPage_whenRedisAccessFails() {
        doThrow(DataAccessResourceFailureException("redis down"))
            .whenever(rankingZSetReader).reverseRange(any(), any(), any())
        whenever(rankingFallbackDailyJpaRepository.findByRankingDateOrderByScoreDescProductIdAsc(any(), any()))
            .thenReturn(listOf(RankingFallbackDaily(today, 1L, BigDecimal("0.7"))))
        whenever(productRepository.findAllActiveByIdIn(listOf(1L))).thenReturn(emptyList())

        val page = service.getPage(date = null, page = 1, size = 10)

        assertAll(
            { assertThat(page.date).isEqualTo(today) },
            { assertThat(page.items).isEmpty() },
        )
    }

    @DisplayName("Redis 접속 실패면 개별 순위도 MySQL COUNT 로 전환되고, 동점 동순위 계약이 유지된다.")
    @Test
    fun fallsBackToMysqlRank_whenRedisAccessFails() {
        doThrow(DataAccessResourceFailureException("redis down")).whenever(rankingZSetReader).score(any(), any())
        whenever(rankingFallbackDailyJpaRepository.findByRankingDateAndProductId(today, 1L))
            .thenReturn(RankingFallbackDaily(today, 1L, BigDecimal("0.5")))
        whenever(rankingFallbackDailyJpaRepository.countByRankingDateAndScoreGreaterThan(today, BigDecimal("0.5")))
            .thenReturn(1L)

        assertThat(service.findTodayRank(1L)).isEqualTo(2L)
    }

    @DisplayName("접속 실패가 아닌 예외는 fallback 하지 않고 그대로 전파한다.")
    @Test
    fun propagatesNonConnectivityFailures() {
        doThrow(IllegalStateException("bug")).whenever(rankingZSetReader).reverseRange(any(), any(), any())

        assertThatThrownBy { service.getPage(date = null, page = 1, size = 10) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
