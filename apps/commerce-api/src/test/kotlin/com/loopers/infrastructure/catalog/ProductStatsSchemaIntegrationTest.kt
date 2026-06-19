package com.loopers.infrastructure.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class ProductStatsSchemaIntegrationTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
) {
    @DisplayName("product_stats 는 좋아요 수 내림차순 상품 목록 조회용 인덱스를 가진다.")
    @Test
    fun hasLikesDescProductListIndex() {
        val columns = jdbcTemplate.query(
            """
            select column_name, collation, seq_in_index
              from information_schema.statistics
             where table_schema = database()
               and table_name = 'product_stats'
               and index_name = 'idx_product_stats_deleted_like_count_product_id'
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
            IndexColumn(name = "deleted_at", collation = "A", sequence = 1),
            IndexColumn(name = "like_count", collation = "D", sequence = 2),
            IndexColumn(name = "product_id", collation = "A", sequence = 3),
        )
    }

    private data class IndexColumn(
        val name: String,
        val collation: String,
        val sequence: Int,
    )
}
