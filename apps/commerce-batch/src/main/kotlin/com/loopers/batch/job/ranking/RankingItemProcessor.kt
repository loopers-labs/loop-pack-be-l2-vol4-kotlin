package com.loopers.batch.job.ranking

import org.springframework.batch.item.ItemProcessor

// Hides: the deterministic fail-before-third incident seam used to prove restart behavior.
open class RankingItemProcessor(private val injectFailure: Boolean) :
    ItemProcessor<RankingItemReader.SourceRow, RankingItemReader.SourceRow> {
    override open fun process(item: RankingItemReader.SourceRow): RankingItemReader.SourceRow {
        if (injectFailure && item.seq == 3L) error("controlled failure before third item")
        return item
    }
}
