package com.loopers.infrastructure.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class ProductSchemaIntegrationTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
) {
    @DisplayName("products 는 브랜드별 상품 목록 조회용 brand_id 인덱스를 가진다.")
    @Test
    fun hasBrandIdIndex() {
        val columns = jdbcTemplate.query(
            """
            select column_name, collation, seq_in_index
              from information_schema.statistics
             where table_schema = database()
               and table_name = 'products'
               and index_name = 'idx_products_brand_id'
             order by seq_in_index
            """.trimIndent(),
        ) { rs, _ ->
            IndexColumn(
                name = rs.getString("column_name"),
                collation = rs.getString("collation"),
                sequence = rs.getInt("seq_in_index"),
            )
        }

        assertThat(columns).containsExactly(
            IndexColumn(name = "brand_id", collation = "A", sequence = 1),
        )
    }

    private data class IndexColumn(
        val name: String,
        val collation: String,
        val sequence: Int,
    )
}
