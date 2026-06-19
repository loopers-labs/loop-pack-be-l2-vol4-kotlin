package com.loopers.projection.product

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ProductLikeCountCommandRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun createInitial(productId: Long, brandId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO product_like_counts (product_id, brand_id, like_count)
            VALUES (?, ?, 0)
            ON DUPLICATE KEY UPDATE
                brand_id = VALUES(brand_id)
            """.trimIndent(),
            productId,
            brandId,
        )
    }

    fun deleteByProductId(productId: Long) {
        jdbcTemplate.update(
            "DELETE FROM product_like_counts WHERE product_id = ?",
            productId,
        )
    }
}
