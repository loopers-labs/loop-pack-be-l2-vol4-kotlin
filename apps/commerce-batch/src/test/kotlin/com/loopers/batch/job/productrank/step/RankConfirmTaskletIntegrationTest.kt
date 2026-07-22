package com.loopers.batch.job.productrank.step

import com.loopers.batch.job.productrank.RankPeriod
import com.loopers.utils.DatabaseCleanUp
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
class RankConfirmTaskletIntegrationTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val aggregatedDate = LocalDate.of(2026, 7, 13)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun tasklet(period: RankPeriod = RankPeriod.WEEKLY) =
        RankConfirmTasklet(jdbcTemplate, period, aggregatedDate)

    private fun execute(period: RankPeriod = RankPeriod.WEEKLY) {
        tasklet(period).execute(mockk(relaxed = true), mockk(relaxed = true))
    }

    private fun seedStaging(vararg pairs: Pair<Long, Long>) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO product_rank_staging (product_id, score) VALUES (?, ?)",
            pairs.map { arrayOf<Any>(it.first, it.second) },
        )
    }

    /** productId 1..count, score는 productId × 10 — productId가 클수록 고득점. */
    private fun seedStagingSequential(count: Int) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO product_rank_staging (product_id, score) VALUES (?, ?)",
            (1..count).map { arrayOf<Any>(it.toLong(), it * 10L) },
        )
    }

    @DisplayName("staging에 100개 초과 상품이 있으면, 점수 상위 100개만 rank 1~100으로 MV에 저장된다.")
    @Test
    fun confirmsTop100_whenStagingExceeds100() {
        seedStagingSequential(150)

        execute()

        val rows = jdbcTemplate.queryForList(
            "SELECT rank_no, product_id, score FROM mv_product_rank_weekly WHERE aggregated_date = ? ORDER BY rank_no",
            aggregatedDate,
        )
        assertThat(rows).hasSize(100)
        assertThat(rows.first()["rank_no"]).isEqualTo(1)
        assertThat(rows.first()["product_id"]).isEqualTo(150L) // 최고점 상품
        assertThat(rows.last()["rank_no"]).isEqualTo(100)
        assertThat(rows.last()["product_id"]).isEqualTo(51L) // 100위
    }

    @DisplayName("같은 aggregated_date로 재실행하면, 기존 스냅샷이 통째로 교체되어 중복이 없다 (멱등).")
    @Test
    fun replacesSnapshot_whenReExecuted() {
        seedStagingSequential(5)
        execute()

        // 점수가 바뀐 두 번째 실행 — 상품 6이 새로 1위
        jdbcTemplate.update("INSERT INTO product_rank_staging (product_id, score) VALUES (6, 999)")
        execute()

        val rows = jdbcTemplate.queryForList(
            "SELECT rank_no, product_id FROM mv_product_rank_weekly WHERE aggregated_date = ? ORDER BY rank_no",
            aggregatedDate,
        )
        assertThat(rows).hasSize(6)
        assertThat(rows.first()["product_id"]).isEqualTo(6L)
    }

    @DisplayName("동점이면 product_id 오름차순으로 rank가 결정된다 (결정적 tie-break).")
    @Test
    fun breaksTieByProductId_whenScoresEqual() {
        seedStaging(30L to 100L, 10L to 100L, 20L to 100L)

        execute()

        val productIds = jdbcTemplate.queryForList(
            "SELECT product_id FROM mv_product_rank_weekly WHERE aggregated_date = ? ORDER BY rank_no",
            Long::class.java,
            aggregatedDate,
        )
        assertThat(productIds).containsExactly(10L, 20L, 30L)
    }

    @DisplayName("MONTHLY 기간이면 mv_product_rank_monthly에 저장되고, weekly 테이블은 건드리지 않는다.")
    @Test
    fun writesToMonthlyTable_whenPeriodIsMonthly() {
        seedStagingSequential(3)

        execute(RankPeriod.MONTHLY)

        val monthlyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE aggregated_date = ?",
            Long::class.java,
            aggregatedDate,
        )
        val weeklyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_weekly", Long::class.java)
        assertThat(monthlyCount).isEqualTo(3L)
        assertThat(weeklyCount).isEqualTo(0L)
    }

    @DisplayName("다른 aggregated_date의 기존 스냅샷은 보존된다 (기간별 이력).")
    @Test
    fun preservesOtherSnapshots_whenDifferentAggregatedDate() {
        seedStagingSequential(2)
        execute()

        val otherDate = aggregatedDate.plusWeeks(1)
        RankConfirmTasklet(jdbcTemplate, RankPeriod.WEEKLY, otherDate)
            .execute(mockk(relaxed = true), mockk(relaxed = true))

        val total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_weekly", Long::class.java)
        assertThat(total).isEqualTo(4L) // 2행 × 스냅샷 2개
    }
}
