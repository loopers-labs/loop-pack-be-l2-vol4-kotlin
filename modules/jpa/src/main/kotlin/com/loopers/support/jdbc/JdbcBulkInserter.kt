package com.loopers.support.jdbc

import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement

/**
 * 제네릭 JDBC bulk insert 유틸. 항목을 청크 단위로 잘라 [JdbcTemplate.batchUpdate] 로 적재한다.
 * (데이터소스의 rewriteBatchedStatements 설정과 함께 멀티-밸류 INSERT 로 재작성된다.)
 *
 * 도메인별 적재 로직은 이 컴포넌트를 주입받아 SQL 과 row binder 만 제공하면 된다.
 */
@Component
class JdbcBulkInserter(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun <T> bulkInsert(
        sql: String,
        items: List<T>,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        bind: (PreparedStatement, T) -> Unit,
    ): Int {
        require(chunkSize > 0) { "chunkSize 는 1 이상이어야 합니다." }
        if (items.isEmpty()) {
            return 0
        }
        var inserted = 0
        items.chunked(chunkSize).forEach { chunk ->
            jdbcTemplate.batchUpdate(
                sql,
                object : BatchPreparedStatementSetter {
                    override fun setValues(ps: PreparedStatement, i: Int) = bind(ps, chunk[i])

                    override fun getBatchSize(): Int = chunk.size
                },
            )
            inserted += chunk.size
        }
        return inserted
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 2_000
    }
}
