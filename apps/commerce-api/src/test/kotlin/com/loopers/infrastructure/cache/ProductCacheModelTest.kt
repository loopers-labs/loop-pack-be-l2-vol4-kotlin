package com.loopers.infrastructure.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.product.Product
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 캐시 DTO 직렬화 라운드트립 단위 테스트.
 * domain ↔ DTO ↔ JSON ↔ DTO ↔ domain 변환이 손실 없이 동작하는지 검증한다.
 */
class ProductCacheModelTest {
    private val objectMapper = jacksonObjectMapper()

    @DisplayName("Product → DTO → JSON → DTO → Product 라운드트립 시 값이 보존된다.")
    @Test
    fun productRoundTrip() {
        val product = Product(id = 7L, name = "에어맥스", price = 100000L, description = "설명", brandId = 3L)

        val json = objectMapper.writeValueAsString(ProductCacheModel.from(product))
        val restored = objectMapper.readValue(json, object : TypeReference<ProductCacheModel>() {}).toDomain()

        assertThat(restored).isEqualTo(product)
    }

    @DisplayName("PageResult<Product> → DTO → JSON → DTO → PageResult 라운드트립 시 값이 보존된다.")
    @Test
    fun pageRoundTrip() {
        val page = PageResult.of(
            items = listOf(
                Product(id = 1L, name = "a", price = 100L, description = "d", brandId = 1L),
                Product(id = 2L, name = "b", price = 200L, description = "d", brandId = 1L),
            ),
            pageRequest = PageRequest(page = 0, size = 20),
            totalElements = 2L,
        )

        val json = objectMapper.writeValueAsString(ProductPageCacheModel.from(page))
        val restored = objectMapper.readValue(json, object : TypeReference<ProductPageCacheModel>() {}).toDomain()

        assertThat(restored).isEqualTo(page)
    }
}
