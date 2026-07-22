package com.loopers.domain.ranking.application

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.ranking.application.command.RankingSearchCommand
import com.loopers.domain.ranking.application.info.RankingItemInfo
import com.loopers.domain.ranking.port.ProductRankingReader
import com.loopers.domain.ranking.port.ProductRankingSnapshotReader
import com.loopers.domain.ranking.vo.RankingKey
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingFacade(
    private val productRankingReader: ProductRankingReader,
    private val productRankingSnapshotReader: ProductRankingSnapshotReader,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
) {
    fun findRankings(command: RankingSearchCommand): PageResult<RankingItemInfo> {
        val (productIds, totalCount) = if (isServedByRedis(command.date)) {
            productRankingReader.findProductIds(command.date, command.page, command.size) to
                productRankingReader.count(command.date)
        } else {
            productRankingSnapshotReader.findProductIds(command.date, command.page, command.size) to
                productRankingSnapshotReader.count(command.date)
        }
        return PageResult(
            content = toRankingItems(productIds, offset = command.page.toLong() * command.size),
            page = command.page,
            size = command.size,
            totalElements = totalCount,
        )
    }

    private fun isServedByRedis(date: LocalDate): Boolean {
        val yesterday = LocalDate.now(RankingKey.ZONE).minusDays(1)
        return !date.isBefore(yesterday)
    }

    private fun toRankingItems(
        productIds: List<Long>,
        offset: Long,
    ): List<RankingItemInfo> {
        if (productIds.isEmpty()) {
            return emptyList()
        }
        val productsById = productService.findByIds(productIds).associateBy { it.id }
        val brandsById = brandService.getByIds(productsById.values.map { it.brandId }.toSet())
            .associateBy { it.id }
        val likeCounts = likeService.countByProductIds(productsById.keys)
        return productIds.mapIndexedNotNull { index, productId ->
            val product = productsById[productId] ?: return@mapIndexedNotNull null
            val brand = brandsById[product.brandId] ?: return@mapIndexedNotNull null
            RankingItemInfo(
                rank = offset + index + 1,
                productId = product.id,
                name = product.name.value,
                price = product.price.value,
                saleType = product.saleType.name,
                brandId = product.brandId,
                brandName = brand.name.value,
                likeCount = likeCounts[product.id] ?: 0L,
            )
        }
    }
}
