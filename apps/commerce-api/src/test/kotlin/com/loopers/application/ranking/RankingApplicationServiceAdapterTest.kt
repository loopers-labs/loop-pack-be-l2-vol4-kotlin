package com.loopers.application.ranking

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingRolloverPort
import com.loopers.domain.ranking.RankingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class RankingApplicationServiceAdapterTest {

    private lateinit var rankingService: RankingService
    private lateinit var productRepositoryPort: ProductRepositoryPort
    private lateinit var rankingRolloverPort: RankingRolloverPort
    private lateinit var adapter: RankingApplicationServiceAdapter

    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val yesterday = today.minusDays(1)

    @BeforeEach
    fun setUp() {
        rankingService = mockk()
        productRepositoryPort = mockk()
        rankingRolloverPort = mockk()
        adapter = RankingApplicationServiceAdapter(rankingService, productRepositoryPort, rankingRolloverPort)

        every { productRepositoryPort.findAllByIds(any()) } returns emptyList()
    }

    private fun emptyPage(date: LocalDate) = RankingPage(date, 1, 20, 0L, emptyList())

    @DisplayName("오늘 보드가 존재하면 요청한 날짜 그대로 조회하고, 복구는 트리거되지 않는다.")
    @Test
    fun servesRequestedDate_whenTodayBoardExists() {
        every { rankingService.exists(today) } returns true
        every { rankingService.getPage(today, 1, 20) } returns emptyPage(today)

        adapter.getRankingPage(RankingPageCommand(date = today, page = 1, size = 20))

        verify(exactly = 1) { rankingService.getPage(today, 1, 20) }
        verify(exactly = 0) { rankingRolloverPort.tryLock(any()) }
    }

    @DisplayName("과거 날짜 조회는 보드 존재 확인 없이 그 날짜로 조회한다.")
    @Test
    fun servesPastDate_withoutExistsCheck() {
        every { rankingService.getPage(yesterday, 1, 20) } returns emptyPage(yesterday)

        adapter.getRankingPage(RankingPageCommand(date = yesterday, page = 1, size = 20))

        verify(exactly = 0) { rankingService.exists(any()) }
        verify(exactly = 1) { rankingService.getPage(yesterday, 1, 20) }
    }

    @DisplayName("오늘 보드 키가 없으면(이월 실패) 전날 보드로 폴백하고, 락 획득 시 복구를 비동기 실행한다.")
    @Test
    fun fallsBackToYesterdayAndRecovers_whenTodayBoardMissing() {
        every { rankingService.exists(today) } returns false
        every { rankingService.getPage(yesterday, 1, 20) } returns emptyPage(yesterday)
        every { rankingRolloverPort.tryLock(today) } returns true
        every { rankingRolloverPort.carryOverSnapshot(yesterday, today) } returns Unit
        every { rankingRolloverPort.releaseLock(today) } returns Unit

        val result = adapter.getRankingPage(RankingPageCommand(date = null, page = 1, size = 20))

        // 응답은 요청 날짜(오늘)로 유지하되 데이터는 전날 보드에서 온다
        assertThat(result.date).isEqualTo(today)
        verify(exactly = 1) { rankingService.getPage(yesterday, 1, 20) }
        verify(timeout = 3_000L) { rankingRolloverPort.carryOverSnapshot(yesterday, today) }
        verify(timeout = 3_000L) { rankingRolloverPort.releaseLock(today) }
    }

    @DisplayName("락 획득에 실패하면(다른 주체가 복구 중) 복구 없이 폴백 응답만 한다.")
    @Test
    fun skipsRecovery_whenLockNotAcquired() {
        every { rankingService.exists(today) } returns false
        every { rankingService.getPage(yesterday, 1, 20) } returns emptyPage(yesterday)
        every { rankingRolloverPort.tryLock(today) } returns false

        adapter.getRankingPage(RankingPageCommand(date = today, page = 1, size = 20))

        verify(exactly = 0) { rankingRolloverPort.carryOverSnapshot(any(), any()) }
    }

    @DisplayName("랭킹 항목은 상품 정보(findAllByIds 배치 조회)로 hydration되고, 없는 상품은 name/price가 null이다.")
    @Test
    fun hydratesItems_withProductInfo() {
        val entries = listOf(
            RankingEntry(productId = 101L, score = 1280.0, rank = 1L),
            RankingEntry(productId = 999L, score = 500.0, rank = 2L),
        )
        val product = Product(id = 101L, name = "에어맥스", price = 39000L, description = "d", brandId = 1L)
        every { rankingService.exists(today) } returns true
        every { rankingService.getPage(today, 1, 20) } returns RankingPage(today, 1, 20, 2L, entries)
        every { productRepositoryPort.findAllByIds(listOf(101L, 999L)) } returns listOf(product)

        val result = adapter.getRankingPage(RankingPageCommand(date = today, page = 1, size = 20))

        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].productName).isEqualTo("에어맥스")
        assertThat(result.items[0].price).isEqualTo(39000L)
        assertThat(result.items[1].productName).isNull()
        assertThat(result.items[1].price).isNull()
    }
}
