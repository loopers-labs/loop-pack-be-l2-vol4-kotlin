package com.loopers.domain.product

import com.loopers.fixture.product.ProductBrandFixture
import com.loopers.domain.inventory.Inventory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

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

        @DisplayName("재고가 함께 전달되면 상품 상세 구성 요소에 포함한다")
        @Test
        fun returnsProductCatalogWithInventory() {
            val product = ProductBrandFixture.createProduct(id = 1L, brandId = 10L)
            val brand = ProductBrandFixture.createBrand(id = 10L)
            val productStat = ProductBrandFixture.createProductStat(productId = 1L, likeCount = 7L)
            val inventory = Inventory(productId = 1L, quantity = 5L)

            val result = productCatalogService.displayForAdmin(
                product = product,
                brand = brand,
                productStat = productStat,
                inventory = inventory,
            )

            assertThat(result.inventory).isEqualTo(inventory)
        }
    }
}
