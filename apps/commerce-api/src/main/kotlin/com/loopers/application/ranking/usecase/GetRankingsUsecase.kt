package com.loopers.application.ranking.usecase

import com.loopers.application.ranking.RankingItemInfo
import com.loopers.application.ranking.RankingPageInfo
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class GetRankingsUsecase(
    private val rankingQueryRepository: RankingQueryRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun execute(query: Query): RankingPageInfo {
        if (query.page < 1) throw CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.")
        if (query.size !in 1..MAX_SIZE) throw CoreException(ErrorType.BAD_REQUEST, "size는 1~${MAX_SIZE}이어야 합니다.")

        val date = query.period.resolveDate(query.date, ZonedDateTime.now())
        val key = query.period.key(date)
        val offset = (query.page - 1L) * query.size

        val ranked = rankingQueryRepository.page(key, offset, query.size.toLong())
        val totalCount = rankingQueryRepository.total(key)
        val productsById = productRepository.findActiveAllByIds(ranked.map { it.productId }).associateBy { it.id }
        val brandIds = productsById.values.map { it.brandId }.distinct()
        val brandNamesById = brandRepository.findActiveAllByIds(brandIds).associate { it.id to it.name }

        // ZSET 순서 유지, 삭제된 상품은 스킵(스펙 §6 — 페이지 항목 수가 size보다 작아질 수 있음)
        val items = ranked.mapIndexedNotNull { index, rankedProduct ->
            val product = productsById[rankedProduct.productId] ?: return@mapIndexedNotNull null
            val brandName = brandNamesById[product.brandId] ?: return@mapIndexedNotNull null
            RankingItemInfo(
                rank = offset + index + 1,
                productId = product.id,
                name = product.name,
                price = product.price,
                brandName = brandName,
                likeCount = product.likeCount,
                score = rankedProduct.score,
            )
        }
        return RankingPageInfo(
            items = items,
            period = query.period,
            date = date,
            page = query.page,
            size = query.size,
            totalCount = totalCount,
        )
    }

    data class Query(
        val period: RankingPeriod,
        val date: String?,
        val page: Int,
        val size: Int,
    )

    companion object {
        private const val MAX_SIZE = 100
    }
}
