package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal

class ProductCatalogDomainServiceTest {
    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    inner class GetDetail {
        @DisplayName("상품과 브랜드 정보를 조합하고 좋아요 수를 함께 반환한다.")
        @Test
        fun returnsProductDetailWithBrandAndLikeCount() {
            // arrange
            val brand = BrandModel(name = "Nike", description = "Brand").withId(1L)
            val product = product(name = "Air Max", likeCount = 5).withId(10L)
            val service = ProductCatalogDomainService()

            // act
            val detail = service.getDetail(product = product, brand = brand)

            // assert
            assertAll(
                { assertThat(detail.product.name).isEqualTo("Air Max") },
                { assertThat(detail.brand.name).isEqualTo("Nike") },
                { assertThat(detail.product.likeCount).isEqualTo(5) },
            )
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    inner class GetProducts {
        @DisplayName("상품 목록과 브랜드 목록을 조합한다.")
        @Test
        fun combinesProductsAndBrands() {
            // arrange
            val brand = BrandModel(name = "Nike", description = "Brand").withId(1L)
            val products = listOf(
                product(name = "A", likeCount = 1).withId(1L),
                product(name = "B", likeCount = 10).withId(2L),
            )
            val service = ProductCatalogDomainService()

            // act
            val details = service.getDetails(products = products, brandsById = mapOf(brand.id to brand))

            // assert
            assertThat(details.map { it.brand.name }).containsExactly("Nike", "Nike")
        }
    }

    private fun product(name: String, likeCount: Int): ProductModel {
        return ProductModel(
            brandId = 1L,
            name = name,
            description = "Product",
            price = BigDecimal("1000.00"),
            likeCount = likeCount,
        )
    }
}
