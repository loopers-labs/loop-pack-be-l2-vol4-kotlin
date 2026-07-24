package com.loopers.domain.ranking

import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.ranking.ProductRankJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * 랭킹 조회 서비스.
 * - DAILY: Redis ZSET에서 Top-N 조회
 * - WEEKLY/MONTHLY: MV 테이블에서 조회
 */
@Component
class RankingQueryService(
    private val rankingQueryRepository: RankingQueryRepository,
    private val productRankJpaRepository: ProductRankJpaRepository,
    private val productService: ProductService,
) {

    /**
     * 기간별 랭킹 페이지를 조회한다.
     *
     * @param period 기간 유형 (DAILY/WEEKLY/MONTHLY). null이면 DAILY.
     * @param date 조회 대상 날짜 (yyyyMMdd). null이면 오늘.
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     * @return 랭킹 상품 목록
     */
    fun getRankingPage(period: String?, date: String?, page: Int, size: Int): List<RankingProductInfo> {
        val rankingPeriod = period?.uppercase() ?: "DAILY"

        return when (rankingPeriod) {
            "DAILY" -> getDailyRanking(date, page, size)
            "WEEKLY" -> getMvRanking(PeriodType.WEEKLY, date, page, size)
            "MONTHLY" -> getMvRanking(PeriodType.MONTHLY, date, page, size)
            else -> getDailyRanking(date, page, size)
        }
    }

    /**
     * 특정 상품의 오늘 순위를 조회한다.
     *
     * @param productId 상품 ID
     * @return 순위 (1-based). 랭킹에 없으면 null.
     */
    fun getProductRank(productId: Long): RankingInfo? {
        val today = todayDate()
        val rank = rankingQueryRepository.getRank(today, productId) ?: return null
        val score = rankingQueryRepository.getScore(today, productId)
        return RankingInfo(rank = rank, score = score ?: 0.0)
    }

    /**
     * 일간 랭킹: Redis ZSET 기반 조회.
     */
    private fun getDailyRanking(date: String?, page: Int, size: Int): List<RankingProductInfo> {
        val targetDate = date ?: todayDate()
        val offset = (page * size).toLong()

        val entries = rankingQueryRepository.getTopN(targetDate, offset, size.toLong())
        if (entries.isEmpty()) return emptyList()

        val productIds = entries.map { it.productId }
        val products = productService.getProductsByIds(productIds)

        return entries.mapIndexed { index, entry ->
            val product = products[entry.productId]
            RankingProductInfo(
                rank = offset + index + 1,
                productId = entry.productId,
                productName = product?.name,
                price = product?.price,
                score = entry.score,
            )
        }
    }

    /**
     * 주간/월간 랭킹: MV 테이블 기반 조회.
     */
    private fun getMvRanking(periodType: PeriodType, date: String?, page: Int, size: Int): List<RankingProductInfo> {
        val targetDate = if (date != null) {
            LocalDate.parse(date, DATE_FORMAT)
        } else {
            LocalDate.now()
        }

        val periodKey = when (periodType) {
            PeriodType.WEEKLY -> toWeekKey(targetDate)
            PeriodType.MONTHLY -> toMonthKey(targetDate)
        }

        val allRankings = productRankJpaRepository
            .findByPeriodTypeAndPeriodKeyOrderByRankingAsc(periodType, periodKey)

        val offset = page * size
        val paged = allRankings.drop(offset).take(size)
        if (paged.isEmpty()) return emptyList()

        val productIds = paged.map { it.productId }
        val products = productService.getProductsByIds(productIds)

        return paged.map { rank ->
            val product = products[rank.productId]
            RankingProductInfo(
                rank = rank.ranking.toLong(),
                productId = rank.productId,
                productName = product?.name,
                price = product?.price,
                score = rank.score,
            )
        }
    }

    private fun todayDate(): String = LocalDate.now().format(DATE_FORMAT)

    private fun toWeekKey(date: LocalDate): String {
        val weekFields = WeekFields.of(Locale.getDefault())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    private fun toMonthKey(date: LocalDate): String {
        return "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

/**
 * 랭킹 페이지 항목 정보.
 */
data class RankingProductInfo(
    val rank: Long,
    val productId: Long,
    val productName: String?,
    val price: Long?,
    val score: Double,
)

/**
 * 개별 상품의 랭킹 정보.
 */
data class RankingInfo(
    val rank: Long,
    val score: Double,
)
