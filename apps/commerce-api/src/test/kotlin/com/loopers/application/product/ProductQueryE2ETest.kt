package com.loopers.application.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql("classpath:week4/product-query-seed.sql")
class ProductQueryE2ETest @Autowired constructor(
    private val service: ProductQueryService,
    private val db: JdbcTemplate,
    private val http: TestRestTemplate,
) {
    @Test
    fun fixedSeedPlansAndOrderedResultAreStableBeforeAndAfterIndex() {
        assertThat(db.queryForObject("select count(*) from products", Long::class.java)).isEqualTo(10_000)
        assertThat(db.queryForObject("select sum(price) from products", Long::class.java)).isEqualTo(472_685_000)
        assertThat(db.queryForObject("select count(*) from products where brand_id=7", Long::class.java)).isEqualTo(100)
        assertThat(db.queryForObject("select sum(price) from products where brand_id=7", Long::class.java)).isEqualTo(4_647_200)

        val before = service.find(7, 20)
        assertThat(explain()).contains("actual time").contains("rows=20")
        assertThat(checksum(before)).isEqualTo(RESPONSE_SHA)
        db.execute("create index idx_products_brand_price_id on products(brand_id,price,id)")
        db.execute("analyze table products")
        service.priceChanged(7)
        val after = service.find(7, 20)
        assertThat(explain()).contains("idx_products_brand_price_id").contains("actual time")
        assertThat(checksum(after)).isEqualTo(RESPONSE_SHA)
        assertThat(after).isEqualTo(before)

        assertThat(http.getForEntity(
            "/api/v1/products?brandId=7&sort=price_asc&limit=20",
            String::class.java,
        ).statusCode.is2xxSuccessful).isTrue()
        db.update("update products set price=0 where id=?", before.first().id)
        service.priceChanged(7)
        assertThat(service.find(7, 20).first().price).isZero()
    }

    private fun explain(): String = db.queryForList(
        "explain analyze select id,brand_id,name,price from products where brand_id=7 order by price,id limit 20",
        String::class.java,
    ).joinToString("\n")

    private fun checksum(rows: List<ProductQueryService.Result>): String {
        val json = rows.joinToString(separator = ",", prefix = "[", postfix = "]") { "{\"id\":${it.id},\"price\":${it.price}}" }
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(json.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    companion object {
        const val RESPONSE_SHA = "46c0bdfab8b815a0bef93fd4b09ebbe37e45ae79a61279e6ac7d2792d544ee02"
    }
}
