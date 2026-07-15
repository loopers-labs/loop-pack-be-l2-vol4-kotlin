package com.loopers.application.ranking

import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingRolloverPort
import com.loopers.domain.ranking.RankingService
import com.loopers.interfaces.api.ranking.RankingApplicationServicePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

@Service
class RankingApplicationServiceAdapter(
    private val rankingService: RankingService,
    private val productRepositoryPort: ProductRepositoryPort,
    private val rankingRolloverPort: RankingRolloverPort,
) : RankingApplicationServicePort {
    private val log = LoggerFactory.getLogger(javaClass)

    private val recoveryExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ranking-rollover-recovery").apply { isDaemon = true }
    }

    override fun getRankingPage(command: RankingPageCommand): RankingPageResult {
        val today = LocalDate.now(ZONE)
        val requestedDate = command.date ?: today
        val effectiveDate = resolveEffectiveDate(requestedDate, today)
        val rankingPage = rankingService.getPage(effectiveDate, command.page, command.size)
        return hydrate(requestedDate, rankingPage)
    }

    /**
     * 오늘 보드 키 자체가 없으면 이월 배치 실패로 판단한다 — 분산 락을 잡은 요청만 복구를 비동기 트리거하고,
     * 복구가 끝날 때까지는 전날 보드로 폴백해 빈 랭킹 응답을 막는다.
     */
    private fun resolveEffectiveDate(requestedDate: LocalDate, today: LocalDate): LocalDate {
        if (requestedDate != today || rankingService.exists(today)) return requestedDate

        triggerRolloverRecovery(today)
        return today.minusDays(1)
    }

    private fun triggerRolloverRecovery(today: LocalDate) {
        if (!rankingRolloverPort.tryLock(today)) return

        recoveryExecutor.execute {
            runCatching {
                rankingRolloverPort.carryOverSnapshot(fromDate = today.minusDays(1), toDate = today)
                rankingRolloverPort.releaseLock(today)
            }.onFailure {
                // 실패 시 락을 유지해 복구 재시도 폭주를 막는다 (락 TTL 만료 후 자연 재시도)
                log.error("랭킹 이월 복구 실패. targetDate={}", today, it)
            }
        }
    }

    private fun hydrate(responseDate: LocalDate, rankingPage: RankingPage): RankingPageResult {
        val productIds = rankingPage.entries.map { it.productId }
        val productsById = if (productIds.isEmpty()) {
            emptyMap()
        } else {
            productRepositoryPort.findAllByIds(productIds).associateBy { it.id }
        }
        val items = rankingPage.entries.map { entry ->
            val product = productsById[entry.productId]
            RankingItemResult(
                rank = entry.rank,
                productId = entry.productId,
                score = entry.score,
                productName = product?.name,
                price = product?.price,
            )
        }
        return RankingPageResult(
            date = responseDate,
            page = rankingPage.page,
            size = rankingPage.size,
            totalCount = rankingPage.totalCount,
            items = items,
        )
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
    }
}
