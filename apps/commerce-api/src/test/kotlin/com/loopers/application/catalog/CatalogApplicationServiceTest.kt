package com.loopers.application.catalog

import com.loopers.application.catalog.port.CatalogProductCacheInvalidationPort
import com.loopers.domain.catalog.CatalogCommand
import com.loopers.domain.catalog.ProductStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class CatalogApplicationServiceTest {
    private val repositories = FakeCatalogRepositories()
    private val service = repositories.service()

    @DisplayName("상품 생성")
    @Nested
    inner class CreateProduct {
        @DisplayName("삭제되지 않은 Brand 에 상품, 재고, 통계, 상세 이미지를 같은 유스케이스로 생성한다.")
        @Test
        fun createsProductWithDependents() {
            val brand = service.createBrand(CatalogCommand.CreateBrand(name = "Nike"))

            val product = service.createProduct(
                CatalogCommand.CreateProduct(
                    brandId = brand.brandId,
                    name = "Air Max",
                    price = 129000,
                    initialStock = 3,
                    detailImageUrls = listOf("https://cdn.example.com/air-max-1.png", "https://cdn.example.com/air-max-2.png"),
                ),
            )

            assertAll(
                { assertThat(product.productId).isPositive() },
                { assertThat(product.brandId).isEqualTo(brand.brandId) },
                { assertThat(product.status).isEqualTo(ProductStatus.ON_SALE) },
                { assertThat(repositories.stockRepository.findByProductId(product.productId)?.stockQuantity).isEqualTo(3) },
                { assertThat(repositories.statsRepository.findByProductId(product.productId)?.likeCount).isZero() },
                { assertThat(repositories.imageRepository.findByProductId(product.productId)).hasSize(2) },
            )
        }

        @DisplayName("같은 Brand 안에 삭제되지 않은 같은 이름 상품이 있으면 CONFLICT 예외를 던진다.")
        @Test
        fun rejectsDuplicateProductNameInsideBrand() {
            val brand = service.createBrand(CatalogCommand.CreateBrand(name = "Nike"))
            service.createProduct(CatalogCommand.CreateProduct(brand.brandId, "Air Max", 129000, 1, emptyList()))

            val ex = assertThrows<CoreException> {
                service.createProduct(CatalogCommand.CreateProduct(brand.brandId, "Air Max", 99000, 1, emptyList()))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("재고 차감")
    @Nested
    inner class DeductStock {
        @DisplayName("재고가 충분하면 실제 재고를 차감한다.")
        @Test
        fun deductsActualStock() {
            val brand = service.createBrand(CatalogCommand.CreateBrand("Nike"))
            val product = service.createProduct(CatalogCommand.CreateProduct(brand.brandId, "Air Max", 129000, 3, emptyList()))

            service.deductStock(CatalogCommand.ChangeStock(product.productId, 2))

            assertThat(repositories.stockRepository.findByProductId(product.productId)?.stockQuantity).isEqualTo(1)
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외를 던진다.")
        @Test
        fun rejectsInsufficientStock() {
            val brand = service.createBrand(CatalogCommand.CreateBrand("Nike"))
            val product = service.createProduct(CatalogCommand.CreateProduct(brand.brandId, "Air Max", 129000, 1, emptyList()))

            val ex = assertThrows<CoreException> {
                service.deductStock(CatalogCommand.ChangeStock(product.productId, 2))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("캐시 무효화")
    @Nested
    inner class CacheInvalidation {
        @DisplayName("상품 정보가 변경되면 해당 상품 item 캐시를 무효화한다.")
        @Test
        fun evictsProductCacheWhenProductIsUpdated() {
            val cacheInvalidationPort = RecordingCacheInvalidationPort()
            val repositories = FakeCatalogRepositories()
            val service = repositories.service(cacheInvalidationPort)
            val brand = service.createBrand(CatalogCommand.CreateBrand("Nike"))
            val product = service.createProduct(CatalogCommand.CreateProduct(brand.brandId, "Air Max", 129000, 3, emptyList()))

            service.updateProduct(product.productId, CatalogCommand.UpdateProduct("Air Max 90", 139000, emptyList()))

            assertThat(cacheInvalidationPort.evictedProductIds).contains(product.productId)
        }

        @DisplayName("브랜드 정보가 변경되면 해당 브랜드의 cached product items 를 무효화한다.")
        @Test
        fun evictsBrandProductCachesWhenBrandIsUpdated() {
            val cacheInvalidationPort = RecordingCacheInvalidationPort()
            val repositories = FakeCatalogRepositories()
            val service = repositories.service(cacheInvalidationPort)
            val brand = service.createBrand(CatalogCommand.CreateBrand("Nike"))

            service.updateBrand(brand.brandId, CatalogCommand.UpdateBrand("Nike Korea"))

            assertThat(cacheInvalidationPort.evictedBrandIds).containsExactly(brand.brandId)
        }
    }

    private class RecordingCacheInvalidationPort : CatalogProductCacheInvalidationPort {
        val evictedProductIds = mutableListOf<Long>()
        val evictedBrandIds = mutableListOf<Long>()

        override fun evictProduct(productId: Long) {
            evictedProductIds += productId
        }

        override fun evictBrandProducts(brandId: Long) {
            evictedBrandIds += brandId
        }
    }
}
