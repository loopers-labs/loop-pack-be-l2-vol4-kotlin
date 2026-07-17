package com.loopers.domain.ranking.infrastructure.persistence

import com.loopers.domain.ranking.port.ProductRankingSnapshotReader
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ProductRankingSnapshotReaderImpl(
    private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
) : ProductRankingSnapshotReader {
    override fun findProductIds(
        date: LocalDate,
        page: Int,
        size: Int,
    ): List<Long> = productRankingDailyJpaRepository
        .findByIdRankingDateOrderByRankNoAsc(date, PageRequest.of(page, size))
        .map { it.id.productId }

    override fun count(date: LocalDate): Long = productRankingDailyJpaRepository.countByIdRankingDate(date)
}
