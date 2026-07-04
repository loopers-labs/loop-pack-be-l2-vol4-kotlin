package com.loopers.projection.like

import com.loopers.projection.like.application.ProductMetricsDelta
import com.loopers.projection.like.application.ProductMetricsProjectionCommand
import com.loopers.projection.like.application.ProductMetricsProjectionException
import com.loopers.projection.like.application.ProductMetricsProjectionService
import com.loopers.projection.like.application.ProductMetricsProjectionStatus
import com.loopers.projection.like.infrastructure.persistence.ProcessedKafkaEventJpaId
import com.loopers.projection.like.infrastructure.persistence.ProcessedKafkaEventJpaRepository
import com.loopers.projection.like.infrastructure.persistence.ProductLikeCountJpaEntity
import com.loopers.projection.like.infrastructure.persistence.ProductLikeCountJpaRepository
import com.loopers.utils.DatabaseCleanUp
import java.time.ZonedDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@SpringBootTest
class ProductMetricsProjectionServiceIntegrationTest
    @Autowired
    constructor(
        private val productMetricsProjectionService: ProductMetricsProjectionService,
        private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
        private val processedKafkaEventJpaRepository: ProcessedKafkaEventJpaRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val dataSource: DataSource,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `product_metrics는_조회_좋아요_판매_조회수를_같은_projection_row에_저장한다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID))

            val result = productMetricsProjectionService.project(
                command(
                    eventType = "ORDER_PAID_V1",
                    deltas = listOf(ProductMetricsDelta(productId = 상품_ID, salesDelta = 3)),
                ),
            )

            assertThat(result.status).isEqualTo(ProductMetricsProjectionStatus.APPLIED)
            assertThat(tableExists("product_metrics")).isTrue()
            assertThat(tableExists("product_like_counts")).isFalse()
            assertThat(metricValue("like_count")).isZero()
            assertThat(metricValue("sales_count")).isEqualTo(3)
            assertThat(metricValue("view_count")).isZero()
        }

        @Test
        fun `같은_event_id는_중복_처리하지_않는다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID))
            val eventId = UUID.randomUUID()

            val first = productMetricsProjectionService.project(
                command(
                    eventId = eventId,
                    eventType = "PRODUCT_VIEWED_V1",
                    deltas = listOf(ProductMetricsDelta(productId = 상품_ID, viewDelta = 1)),
                ),
            )
            val duplicate = productMetricsProjectionService.project(
                command(
                    eventId = eventId,
                    eventType = "PRODUCT_VIEWED_V1",
                    deltas = listOf(ProductMetricsDelta(productId = 상품_ID, viewDelta = 1)),
                ),
            )

            assertThat(first.status).isEqualTo(ProductMetricsProjectionStatus.APPLIED)
            assertThat(duplicate.status).isEqualTo(ProductMetricsProjectionStatus.DUPLICATE)
            assertThat(metricValue("view_count")).isEqualTo(1)
            assertThat(processedKafkaEventJpaRepository.count()).isEqualTo(1)
        }

        @Test
        fun `발생시각이_역전된_다른_지표_이벤트도_event_id가_다르면_적용한다`() {
            val newer = ZonedDateTime.parse("2026-07-03T10:00:00+09:00")
            productLikeCountJpaRepository.saveAndFlush(
                ProductLikeCountJpaEntity(
                    productId = 상품_ID,
                    viewCount = 5,
                    lastEventAt = newer,
                    lastViewEventAt = newer,
                ),
            )

            val result = productMetricsProjectionService.project(
                command(
                    eventType = "ORDER_PAID_V1",
                    occurredAt = newer.minusMinutes(1),
                    deltas = listOf(ProductMetricsDelta(productId = 상품_ID, salesDelta = 1)),
                ),
            )

            assertThat(result.status).isEqualTo(ProductMetricsProjectionStatus.APPLIED)
            assertThat(metricValue("view_count")).isEqualTo(5)
            assertThat(metricValue("sales_count")).isEqualTo(1)
        }

        @Test
        fun `발생시각이_역전된_같은_지표_이벤트는_STALE로_무시한다`() {
            val newer = ZonedDateTime.parse("2026-07-03T10:00:00+09:00")
            productLikeCountJpaRepository.saveAndFlush(
                ProductLikeCountJpaEntity(
                    productId = 상품_ID,
                    viewCount = 5,
                    lastEventAt = newer,
                    lastViewEventAt = newer,
                ),
            )

            val result = productMetricsProjectionService.project(
                command(
                    eventType = "PRODUCT_VIEWED_V1",
                    occurredAt = newer.minusMinutes(1),
                    deltas = listOf(ProductMetricsDelta(productId = 상품_ID, viewDelta = 1)),
                ),
            )

            assertThat(result.status).isEqualTo(ProductMetricsProjectionStatus.STALE)
            assertThat(metricValue("view_count")).isEqualTo(5)
        }

        @Test
        fun `카운터_하한을_깨는_이벤트는_처리_이력을_남기지_않고_실패한다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID, likeCount = 0))
            val eventId = UUID.randomUUID()

            assertThatThrownBy {
                productMetricsProjectionService.project(
                    command(
                        eventId = eventId,
                        eventType = "LIKE_COUNT_CHANGED_V1",
                        deltas = listOf(ProductMetricsDelta(productId = 상품_ID, likeDelta = -1)),
                    ),
                )
            }.isInstanceOf(ProductMetricsProjectionException::class.java)

            assertThat(metricValue("like_count")).isZero()
            assertThat(processedKafkaEventJpaRepository.findById(processedEventId(eventId))).isEmpty()
        }

        private fun command(
            eventId: UUID = UUID.randomUUID(),
            eventType: String,
            occurredAt: ZonedDateTime = ZonedDateTime.parse("2026-07-03T09:00:00+09:00"),
            deltas: List<ProductMetricsDelta>,
        ): ProductMetricsProjectionCommand =
            ProductMetricsProjectionCommand(
                eventId = eventId,
                consumerGroup = CONSUMER_GROUP,
                eventType = eventType,
                occurredAt = occurredAt,
                deltas = deltas,
            )

        private fun tableExists(tableName: String): Boolean =
            dataSource.connection.use { connection ->
                connection.metaData.getTables(null, null, null, arrayOf("TABLE")).use { tables ->
                    generateSequence {
                        if (tables.next()) tables.getString("TABLE_NAME") else null
                    }.any { it.equals(tableName, ignoreCase = true) }
                }
            }

        private fun metricValue(columnName: String): Long =
            jdbcTemplate.queryForObject(
                "select $columnName from product_metrics where product_id = ?",
                Long::class.java,
                상품_ID,
            ) ?: 0L

        private fun processedEventId(eventId: UUID): ProcessedKafkaEventJpaId =
            ProcessedKafkaEventJpaId(eventId = eventId, consumerGroup = CONSUMER_GROUP)

        companion object {
            private const val 상품_ID = 10L
            private const val CONSUMER_GROUP = "commerce-streamer-product-metrics"
        }
    }
