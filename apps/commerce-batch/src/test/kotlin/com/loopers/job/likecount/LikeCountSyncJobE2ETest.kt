package com.loopers.job.likecount

import com.loopers.batch.job.likecount.LikeCountSyncJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${LikeCountSyncJobConfig.JOB_NAME}"])
class LikeCountSyncJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(LikeCountSyncJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
) {
    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0")
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS products (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                brand_id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(1000) NOT NULL,
                price BIGINT NOT NULL,
                like_count INT NOT NULL DEFAULT 0,
                created_at DATETIME(6) NOT NULL,
                updated_at DATETIME(6) NOT NULL,
                deleted_at DATETIME(6)
            )
            """,
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS likes (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                created_at DATETIME(6) NOT NULL,
                updated_at DATETIME(6) NOT NULL,
                deleted_at DATETIME(6)
            )
            """,
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS product_like_counts (
                product_id BIGINT PRIMARY KEY,
                brand_id BIGINT NOT NULL,
                like_count INT NOT NULL DEFAULT 0,
                INDEX idx_plc_brand_like (brand_id, like_count DESC)
            )
            """,
        )
        jdbcTemplate.execute("TRUNCATE TABLE product_like_counts")
        jdbcTemplate.execute("TRUNCATE TABLE likes")
        jdbcTemplate.execute("TRUNCATE TABLE products")
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1")
    }

    @DisplayName("likes 테이블의 active 좋아요를 상품별로 집계하여 product_like_counts에 반영한다")
    @Test
    fun shouldSyncLikeCounts() {
        // arrange
        jobLauncherTestUtils.job = job
        insertProduct(id = 1, brandId = 10)
        insertProduct(id = 2, brandId = 10)
        insertProduct(id = 3, brandId = 20)

        insertActiveLike(userId = 1, productId = 1)
        insertActiveLike(userId = 2, productId = 1)
        insertActiveLike(userId = 3, productId = 1)
        insertActiveLike(userId = 1, productId = 2)
        insertCancelledLike(userId = 2, productId = 2)

        // act
        val jobParameters = JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .addString("runId", UUID.randomUUID().toString())
            .toJobParameters()
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

        val counts = jdbcTemplate.queryForList(
            "SELECT product_id, brand_id, like_count FROM product_like_counts ORDER BY product_id",
        )
        assertAll(
            { assertThat(counts).hasSize(3) },
            { assertThat(counts[0]["product_id"]).isEqualTo(1L) },
            { assertThat(counts[0]["brand_id"]).isEqualTo(10L) },
            { assertThat(counts[0]["like_count"]).isEqualTo(3) },
            { assertThat(counts[1]["product_id"]).isEqualTo(2L) },
            { assertThat(counts[1]["like_count"]).isEqualTo(1) },
            { assertThat(counts[2]["product_id"]).isEqualTo(3L) },
            { assertThat(counts[2]["brand_id"]).isEqualTo(20L) },
            { assertThat(counts[2]["like_count"]).isEqualTo(0) },
        )
    }

    @DisplayName("이미 집계된 데이터가 있어도 최신 likes 기준으로 갱신한다")
    @Test
    fun shouldUpdateExistingCounts() {
        // arrange
        jobLauncherTestUtils.job = job
        insertProduct(id = 1, brandId = 10)
        jdbcTemplate.update(
            "INSERT INTO product_like_counts (product_id, brand_id, like_count) VALUES (1, 10, 99)",
        )
        insertActiveLike(userId = 1, productId = 1)
        insertActiveLike(userId = 2, productId = 1)

        // act
        val jobParameters = JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .addString("runId", UUID.randomUUID().toString())
            .toJobParameters()
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

        val likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM product_like_counts WHERE product_id = 1",
            Int::class.java,
        )
        assertThat(likeCount).isEqualTo(2)
    }

    private fun insertProduct(id: Long, brandId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO products (id, brand_id, name, description, price, like_count, created_at, updated_at)
            VALUES (?, ?, 'test', 'test desc', 10000, 0, NOW(6), NOW(6))
            """,
            id,
            brandId,
        )
    }

    private fun insertActiveLike(userId: Long, productId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO likes (user_id, product_id, created_at, updated_at)
            VALUES (?, ?, NOW(6), NOW(6))
            """,
            userId,
            productId,
        )
    }

    private fun insertCancelledLike(userId: Long, productId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO likes (user_id, product_id, created_at, updated_at, deleted_at)
            VALUES (?, ?, NOW(6), NOW(6), NOW(6))
            """,
            userId,
            productId,
        )
    }
}
