package com.loopers.batch.job.ranking

import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.jdbc.core.JdbcTemplate

// Hides: event idempotency, ranking upsert, and deterministic rank recomputation.
open class RankingItemWriter(private val jdbc: JdbcTemplate) : ItemWriter<RankingItemReader.SourceRow> {
    override open fun write(chunk: Chunk<out RankingItemReader.SourceRow>) {
        chunk.forEach { item ->
            val inserted = jdbc.update(
                "insert ignore into ranking_applied_event(snapshot_id,event_id) values (?,?)",
                SNAPSHOT_ID, item.eventId,
            )
            if (inserted == 1) {
                jdbc.update(
                    "insert into weekly_ranking(snapshot_id,product_id,score,ranking_position) values (?,?,?,0) " +
                        "on duplicate key update score=score+values(score)",
                    SNAPSHOT_ID, item.productId, item.scoreDelta,
                )
            }
        }
        jdbc.queryForList(
            "select product_id from weekly_ranking where snapshot_id=? order by score desc,product_id asc",
            Long::class.java, SNAPSHOT_ID,
        ).forEachIndexed { index, productId ->
            jdbc.update(
                "update weekly_ranking set ranking_position=? where snapshot_id=? and product_id=?",
                index + 1, SNAPSHOT_ID, productId,
            )
        }
    }
    companion object { const val SNAPSHOT_ID = "snapshot-2026w34" }
}
