package com.loopers.application.ranking

import com.loopers.application.brand.BrandApplicationService
import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankedProductId
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingRepository
import com.loopers.infrastructure.ranking.RankingKeyGenerator
import com.loopers.projection.product.ProductLikeCountQueryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageCondition
import com.loopers.support.paging.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
@Transactional(readOnly = true)
class RankingFacade(
    private val rankingRepository: RankingRepository,
    private val productRepository: ProductRepository,
    private val brandApplicationService: BrandApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val productLikeCountQueryRepository: ProductLikeCountQueryRepository,
) {
    fun getRankings(period: RankingPeriod, date: LocalDate, pageCondition: PageCondition): PageResult<RankingInfo> {
        return when (period) {
            RankingPeriod.DAILY -> getDailyRankings(date, pageCondition)
            RankingPeriod.WEEKLY -> getPeriodRankings(
                RankingKeyGenerator.weekStart(date),
                pageCondition,
                rankingRepository::findWeeklyTopN,
                rankingRepository::countWeekly,
            )
            RankingPeriod.MONTHLY -> getPeriodRankings(
                RankingKeyGenerator.monthStart(date),
                pageCondition,
                rankingRepository::findMonthlyTopN,
                rankingRepository::countMonthly,
            )
        }
    }

    private fun getDailyRankings(date: LocalDate, pageCondition: PageCondition): PageResult<RankingInfo> {
        val totalElements = rankingRepository.countByDate(date)
        val totalPages = pageCondition.totalPages(totalElements)

        val ranked = rankingRepository.findTopN(date, pageCondition.offset(), pageCondition.limit())
        if (ranked.isEmpty()) {
            return PageResult(
                items = emptyList(),
                page = pageCondition.page,
                size = pageCondition.size,
                totalElements = totalElements,
                totalPages = totalPages,
            )
        }

        val items = enrichRankedProducts(ranked)

        return PageResult(
            items = items,
            page = pageCondition.page,
            size = pageCondition.size,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    private fun getPeriodRankings(
        periodStart: LocalDate,
        pageCondition: PageCondition,
        findTopN: (LocalDate, Long, Long) -> List<RankedProductId>,
        count: (LocalDate) -> Long,
    ): PageResult<RankingInfo> {
        val totalElements = count(periodStart)
        val totalPages = pageCondition.totalPages(totalElements)

        val ranked = findTopN(periodStart, pageCondition.offset(), pageCondition.limit())
        if (ranked.isEmpty()) {
            return PageResult(
                items = emptyList(),
                page = pageCondition.page,
                size = pageCondition.size,
                totalElements = totalElements,
                totalPages = totalPages,
            )
        }

        val items = enrichRankedProducts(ranked)

        return PageResult(
            items = items,
            page = pageCondition.page,
            size = pageCondition.size,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    private fun enrichRankedProducts(ranked: List<RankedProductId>): List<RankingInfo> {
        val productIds = ranked.map { it.productId }
        val productMap = productRepository.findAllByIds(productIds).associateBy { it.id }
        val brandIds = productMap.values.map { it.brandId }.distinct()
        val brandMap = brandApplicationService.getBrands(brandIds).associateBy { it.id }
        val stockMap = stockApplicationService.getStocks(productIds)
        val likeCountMap = productLikeCountQueryRepository.findByProductIdIn(productIds)
            .associate { it.productId to it.likeCount }

        return ranked.mapNotNull { rankedProduct ->
            val product = productMap[rankedProduct.productId] ?: return@mapNotNull null
            val brand = brandMap[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다. id=${product.brandId}")
            val stock = stockMap[rankedProduct.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다. productId=${rankedProduct.productId}")

            RankingInfo(
                rank = rankedProduct.rank,
                productId = rankedProduct.productId,
                name = product.name,
                brandName = brand.name,
                price = product.price.amount,
                likeCount = likeCountMap[rankedProduct.productId] ?: 0,
                soldOut = stock.isSoldOut(),
            )
        }
    }
}
