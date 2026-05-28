package com.loopers.domain.product

import com.loopers.fixture.product.ProductBrandFixture
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductCatalogServiceTest {
    private val productCatalogService = ProductCatalogService()

    @DisplayName("고객 상품 상세 구성")
    @Nested
    inner class Display {
        @DisplayName("노출 가능한 상품과 브랜드면 상품 상세 구성 요소를 반환한다")
        @Test
        fun returnsProductCatalog_whenProductAndBrandAreDisplayable() {
            val product = ProductBrandFixture.createProduct(id = 1L, brandId = 10L)
            val brand = ProductBrandFixture.createBrand(id = 10L)
            val productStat = ProductBrandFixture.createProductStat(productId = 1L, likeCount = 7L)

            val result = productCatalogService.display(product, brand, productStat)

            assertAll(
                { assertThat(result.product).isEqualTo(product) },
                { assertThat(result.brand).isEqualTo(brand) },
                { assertThat(result.productStat).isEqualTo(productStat) },
            )
        }

        @DisplayName("삭제된 상품은 고객 상품 상세로 구성할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val product = ProductBrandFixture.createProduct(id = 1L, brandId = 10L, isDeleted = true)
            val brand = ProductBrandFixture.createBrand(id = 10L)
            val productStat = ProductBrandFixture.createProductStat(productId = 1L)

            val result = assertThrows<CoreException> {
                productCatalogService.display(product, brand, productStat)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드는 고객 상품 상세로 구성할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val product = ProductBrandFixture.createProduct(id = 1L, brandId = 10L)
            val brand = ProductBrandFixture.createBrand(id = 10L, isDeleted = true)
            val productStat = ProductBrandFixture.createProductStat(productId = 1L)

            val result = assertThrows<CoreException> {
                productCatalogService.display(product, brand, productStat)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
