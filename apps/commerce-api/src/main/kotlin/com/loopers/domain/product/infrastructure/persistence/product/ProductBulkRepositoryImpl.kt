package com.loopers.domain.product.infrastructure.persistence.product

import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.port.ProductBulkRepository
import com.loopers.support.jdbc.JdbcBulkInserter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

@Component
class ProductBulkRepositoryImpl(
    private val jdbcBulkInserter: JdbcBulkInserter,
    private val jdbcTemplate: JdbcTemplate,
) : ProductBulkRepository {
    override fun bulkInsert(products: List<ProductModel>): Int {
        val now = Timestamp.from(Instant.now())
        return jdbcBulkInserter.bulkInsert(INSERT_SQL, products) { ps, product ->
            ps.setLong(1, product.brandId)
            ps.setString(2, product.name.value)
            ps.setLong(3, product.price.value)
            ps.setTimestamp(4, now)
            ps.setTimestamp(5, now)
            ps.setTimestamp(6, product.deletedAt?.let { Timestamp.from(it.toInstant()) })
        }
    }

    override fun count(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long::class.java) ?: 0L

    companion object {
        private const val INSERT_SQL =
            "INSERT INTO products (brand_id, product_name, price, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
    }
}
