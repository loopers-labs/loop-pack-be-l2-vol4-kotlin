package com.loopers.projection.ranking.infrastructure.persistence

import com.loopers.projection.ranking.application.RankingEntry
import com.loopers.projection.ranking.port.ProductRankingSnapshotRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Component
class ProductRankingSnapshotRepositoryImpl(
    private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
) : ProductRankingSnapshotRepository {
    @Transactional
    override fun replaceAll(
        date: LocalDate,
        entries: List<RankingEntry>,
    ) {
        productRankingDailyJpaRepository.deleteByIdRankingDate(date)
        productRankingDailyJpaRepository.saveAll(
            entries.mapIndexed { index, entry ->
                ProductRankingDailyJpaEntity(
                    id = ProductRankingDailyJpaId(rankingDate = date, productId = entry.productId),
                    rankNo = index + 1,
                    score = entry.score,
                    updatedAt = Instant.now(),
                )
            },
        )
    }
}
