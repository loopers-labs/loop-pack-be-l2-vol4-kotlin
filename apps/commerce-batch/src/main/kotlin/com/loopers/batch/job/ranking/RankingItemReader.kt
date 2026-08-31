package com.loopers.batch.job.ranking

import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamReader
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

// Hides: stable source ordering and the restart position stored in ExecutionContext.
open class RankingItemReader(private val jdbc: JdbcTemplate) : ItemStreamReader<RankingItemReader.SourceRow> {
    private var rows = emptyList<SourceRow>()
    private var index = 0
    override open fun read(): SourceRow? = rows.getOrNull(index)?.also { index++ }
    override open fun open(executionContext: ExecutionContext) {
        rows = jdbc.query("select seq,event_id,product_id,score_delta,occurred_at from ranking_source order by seq") { rs, _ ->
            SourceRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getTimestamp(5).toLocalDateTime())
        }
        index = executionContext.getInt(INDEX_KEY, 0)
    }
    override open fun update(executionContext: ExecutionContext) { executionContext.putInt(INDEX_KEY, index) }
    override open fun close() { rows = emptyList() }
    data class SourceRow(val seq: Long, val eventId: String, val productId: Long, val scoreDelta: Long, val occurredAt: LocalDateTime)
    companion object { const val INDEX_KEY = "reader.index" }
}
