package com.loopers.infrastructure.productrank

import com.loopers.domain.productrank.ProductRankMonthlyRepository
import com.loopers.domain.productrank.ProductRankWeeklyRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

@Import(MySqlTestContainersConfig::class)
@SpringBootTest
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
class ProductRankMvRepositoryImplIntegrationTest @Autowired constructor(
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("weekly/monthly MV는 baseDate의 TOP 100을 score desc, productId asc 순서로 조회한다")
    @Test
    fun findsTop100OrderedByScoreDescAndProductIdAsc() {
        val weeklyBaseDate = LocalDate.parse("2026-08-03")
        val monthlyBaseDate = LocalDate.parse("2026-08-01")

        (1L..101L).forEach { productId ->
            productRankWeeklyRepository.upsert(weeklyBaseDate, productId, productId.toDouble())
            productRankMonthlyRepository.upsert(monthlyBaseDate, productId, productId.toDouble())
        }
        productRankWeeklyRepository.upsert(weeklyBaseDate, 200L, 101.0)
        productRankMonthlyRepository.upsert(monthlyBaseDate, 200L, 101.0)

        val weeklyTop = productRankWeeklyRepository.findTop100(weeklyBaseDate)
        val monthlyTop = productRankMonthlyRepository.findTop100(monthlyBaseDate)
        assertAll(
            { assertThat(weeklyTop).hasSize(100) },
            { assertThat(weeklyTop.take(3).map { it.productId }).containsExactly(101L, 200L, 100L) },
            { assertThat(monthlyTop).hasSize(100) },
            { assertThat(monthlyTop.take(3).map { it.productId }).containsExactly(101L, 200L, 100L) },
        )
    }
}
