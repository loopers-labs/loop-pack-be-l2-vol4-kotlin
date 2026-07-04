package com.loopers.domain.like.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@SpringBootTest(
    properties = ["commerce-events.outbox-relay.enabled=false"],
)
class ProductMetricsProjectionIntegrationTest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val jdbcTemplate: JdbcTemplate,
        private val dataSource: DataSource,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `상품_등록은_product_metrics_projection_행을_초기화한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())

            val product = productFacade.registerProduct(상품_등록_커맨드(brandId = brand.id))

            assertThat(tableExists("product_metrics")).isTrue()
            assertThat(tableExists("product_like_counts")).isFalse()
            assertThat(metricValue(product.id, "like_count")).isZero()
            assertThat(metricValue(product.id, "sales_count")).isZero()
            assertThat(metricValue(product.id, "view_count")).isZero()
            assertThat(metricColumn(product.id, "updated_at")).isNotNull()
        }

        private fun tableExists(tableName: String): Boolean =
            dataSource.connection.use { connection ->
                connection.metaData.getTables(null, null, null, arrayOf("TABLE")).use { tables ->
                    generateSequence {
                        if (tables.next()) tables.getString("TABLE_NAME") else null
                    }.any { it.equals(tableName, ignoreCase = true) }
                }
            }

        private fun metricValue(
            productId: Long,
            columnName: String,
        ): Long =
            jdbcTemplate.queryForObject(
                "select $columnName from product_metrics where product_id = ?",
                Long::class.java,
                productId,
            ) ?: 0L

        private fun metricColumn(
            productId: Long,
            columnName: String,
        ): Any? =
            jdbcTemplate.queryForObject(
                "select $columnName from product_metrics where product_id = ?",
                Any::class.java,
                productId,
            )
    }
