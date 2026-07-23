package com.loopers.batch.job.productrank.step

import com.loopers.batch.job.productrank.AggregationWindow
import com.loopers.batch.job.productrank.item.AggregatedMetricRow
import com.loopers.batch.job.productrank.item.RawMetricRow
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder
import javax.sql.DataSource

/**
 * 두 집계 전략의 Reader 팩토리.
 * - groupBy: 기간 전체를 DB에서 상품별로 집계해 읽는다. GROUP BY가 있으면 페이징 프로바이더가
 *   쿼리를 서브쿼리로 감싸 매 페이지 집계를 반복한다 — 이 비용이 실험의 관측 대상이다.
 * - raw: 원시 행을 PK 순서(sort key 3개)로 흘려 읽는다. 전송량은 상품수×일수×타입 수만큼 커진다.
 */
object ProductRankReaders {

    fun groupByReader(
        dataSource: DataSource,
        window: AggregationWindow,
        pageSize: Int,
    ): JdbcPagingItemReader<AggregatedMetricRow> =
        JdbcPagingItemReaderBuilder<AggregatedMetricRow>()
            .name("groupByMetricsReader")
            .dataSource(dataSource)
            .selectClause(
                "SELECT product_id, " +
                    "SUM(CASE WHEN type = 'VIEW' THEN count ELSE 0 END) AS view_count, " +
                    "SUM(CASE WHEN type = 'LIKE' THEN count ELSE 0 END) AS like_count, " +
                    "SUM(CASE WHEN type = 'SALES' THEN count ELSE 0 END) AS sales_count",
            )
            .fromClause("FROM product_metrics")
            .whereClause("WHERE metric_date BETWEEN :startDate AND :endDate")
            .groupClause("GROUP BY product_id")
            .sortKeys(mapOf("product_id" to Order.ASCENDING))
            .parameterValues(mapOf("startDate" to window.start, "endDate" to window.endInclusive))
            .pageSize(pageSize)
            .rowMapper { rs, _ ->
                AggregatedMetricRow(
                    productId = rs.getLong("product_id"),
                    viewCount = rs.getLong("view_count"),
                    likeCount = rs.getLong("like_count"),
                    salesCount = rs.getLong("sales_count"),
                )
            }
            .build()
            // 빈으로 등록되지 않고 Step 조립 시 직접 생성되므로, InitializingBean 초기화를 수동 호출한다
            .apply { afterPropertiesSet() }

    fun rawReader(dataSource: DataSource, window: AggregationWindow, pageSize: Int): JdbcPagingItemReader<RawMetricRow> =
        JdbcPagingItemReaderBuilder<RawMetricRow>()
            .name("rawMetricsReader")
            .dataSource(dataSource)
            // sort key 컬럼은 페이지 재시작 값으로 쓰이므로 select에 모두 포함해야 한다
            .selectClause("SELECT product_id, type, metric_date, count")
            .fromClause("FROM product_metrics")
            .whereClause("WHERE metric_date BETWEEN :startDate AND :endDate")
            // 실제 PK 컬럼 순서(metric_date, product_id, type — Hibernate가 EmbeddedId를 알파벳순으로 생성)와
            // 일치해야 페이지 쿼리가 인덱스 순서로 읽는다. 어긋나면 매 페이지 윈도우 전체 filesort → O(N²/pageSize)
            .sortKeys(
                linkedMapOf(
                    "metric_date" to Order.ASCENDING,
                    "product_id" to Order.ASCENDING,
                    "type" to Order.ASCENDING,
                ),
            )
            .parameterValues(mapOf("startDate" to window.start, "endDate" to window.endInclusive))
            .pageSize(pageSize)
            .rowMapper { rs, _ ->
                RawMetricRow(
                    productId = rs.getLong("product_id"),
                    type = rs.getString("type"),
                    count = rs.getLong("count"),
                )
            }
            .build()
            .apply { afterPropertiesSet() }
}
