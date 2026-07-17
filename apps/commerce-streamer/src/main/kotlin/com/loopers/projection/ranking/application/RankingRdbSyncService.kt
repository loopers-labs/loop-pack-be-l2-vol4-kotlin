package com.loopers.projection.ranking.application

import com.loopers.projection.ranking.port.ProductRankingSnapshotRepository
import com.loopers.projection.ranking.port.ProductRankingStore
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RankingRdbSyncService(
    private val productRankingStore: ProductRankingStore,
    private val productRankingSnapshotRepository: ProductRankingSnapshotRepository,
    private val properties: RankingProperties,
) {
    fun sync() {
        val today = LocalDate.now(RankingKey.ZONE)
        val entries = productRankingStore.topEntries(today, properties.rdbSync.topN)
        if (entries.isEmpty()) {
            return
        }
        productRankingSnapshotRepository.replaceAll(today, entries)
    }
}
