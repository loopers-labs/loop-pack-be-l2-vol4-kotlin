package com.loopers.application.product

import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.projection.product.ProductLikeCountProjectionEntity
import com.loopers.projection.product.ProductLikeCountQueryRepository
import com.loopers.support.paging.PageCondition
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(properties = ["product.cache.enabled=true", "product.cache.ttl-seconds=302"])
class ProductCacheIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productCacheService: ProductCacheService,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val productLikeCountQueryRepository: ProductLikeCountQueryRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val jdbcTemplate: JdbcTemplate,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("상품 상세 캐시")
    @Nested
    inner class ProductDetailCache {
        @DisplayName("첫 조회 시 DB에서 가져와 캐시에 저장한다.")
        @Test
        fun cacheMiss_thenCacheHit() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity())
            val product = saveProductWithStock(brandId = brand.id, name = "T-Shirt", stock = 5)

            // act
            val firstResult = productFacade.getProductDetail(product.id)

            // assert
            val cached = productCacheService.getProductDetail(product.id)
            assertAll(
                { assertThat(cached).isNotNull() },
                { assertThat(cached!!.id).isEqualTo(firstResult.id) },
                { assertThat(cached!!.name).isEqualTo("T-Shirt") },
                { assertThat(cached!!.brandName).isEqualTo("Loopers") },
                { assertThat(cached!!.stock).isEqualTo(5) },
            )
        }

        @DisplayName("캐시에 데이터가 있으면 DB 변경과 무관하게 캐시 데이터를 반환한다.")
        @Test
        fun cacheHit_returnsStaleData() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity())
            val product = saveProductWithStock(brandId = brand.id, name = "T-Shirt", price = 10_000L)
            productFacade.getProductDetail(product.id)

            jdbcTemplate.update(
                "UPDATE products SET name = ?, price = ? WHERE id = ?",
                "Updated Name",
                99_999L,
                product.id,
            )

            // act
            val result = productFacade.getProductDetail(product.id)

            // assert
            assertAll(
                { assertThat(result.name).isEqualTo("T-Shirt") },
                { assertThat(result.price).isEqualTo(10_000L) },
            )
        }

        @DisplayName("상품 수정 시 상세 캐시가 삭제되어 다음 조회에서 최신 데이터를 반환한다.")
        @Test
        fun updateProduct_evictsDetailCache() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity())
            val product = saveProductWithStock(brandId = brand.id, name = "T-Shirt", price = 10_000L)
            productFacade.getProductDetail(product.id)

            // act
            productFacade.updateProduct(
                productId = product.id,
                name = "Hoodie",
                description = "따뜻한 후드",
                price = 30_000L,
            )

            // assert
            val afterUpdate = productFacade.getProductDetail(product.id)
            assertAll(
                { assertThat(afterUpdate.name).isEqualTo("Hoodie") },
                { assertThat(afterUpdate.price).isEqualTo(30_000L) },
            )
        }

        @DisplayName("상품 삭제 시 상세 캐시가 삭제된다.")
        @Test
        fun deleteProduct_evictsDetailCache() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity())
            val product = saveProductWithStock(brandId = brand.id)
            productFacade.getProductDetail(product.id)

            // act
            productFacade.deleteProduct(product.id)

            // assert
            val cached = productCacheService.getProductDetail(product.id)
            assertThat(cached).isNull()
        }
    }

    @DisplayName("상품 목록 캐시")
    @Nested
    inner class ProductListCache {
        @DisplayName("같은 조건으로 조회하면 캐시에서 반환한다.")
        @Test
        fun sameCondition_cacheHit() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity())
            saveProductWithStock(brandId = brand.id, name = "T-Shirt", price = 10_000L)
            saveProductWithStock(brandId = brand.id, name = "Hoodie", price = 20_000L)

            val condition = ProductSearchCondition(
                sortType = ProductSortType.PRICE_ASC,
                pageCondition = PageCondition(page = 0, size = 10),
            )

            // act
            productFacade.getProducts(condition)

            // assert
            val cached = productCacheService.getProductList(condition)
            assertAll(
                { assertThat(cached).isNotNull() },
                { assertThat(cached!!.items).hasSize(2) },
                { assertThat(cached!!.items.map { it.name }).containsExactly("T-Shirt", "Hoodie") },
            )
        }

        @DisplayName("다른 조건으로 조회하면 별도 캐시가 생성된다.")
        @Test
        fun differentCondition_separateCache() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity())
            saveProductWithStock(brandId = brand.id, name = "T-Shirt", price = 10_000L)
            saveProductWithStock(brandId = brand.id, name = "Hoodie", price = 20_000L)

            val priceAsc = ProductSearchCondition(
                sortType = ProductSortType.PRICE_ASC,
                pageCondition = PageCondition(page = 0, size = 10),
            )
            val latest = ProductSearchCondition(
                sortType = ProductSortType.LATEST,
                pageCondition = PageCondition(page = 0, size = 10),
            )

            // act
            productFacade.getProducts(priceAsc)
            productFacade.getProducts(latest)

            // assert
            val cachedPriceAsc = productCacheService.getProductList(priceAsc)
            val cachedLatest = productCacheService.getProductList(latest)
            assertAll(
                { assertThat(cachedPriceAsc).isNotNull() },
                { assertThat(cachedLatest).isNotNull() },
                { assertThat(cachedPriceAsc!!.items.map { it.name }).containsExactly("T-Shirt", "Hoodie") },
                { assertThat(cachedLatest!!.items.map { it.name }).containsExactly("Hoodie", "T-Shirt") },
            )
        }
    }

    private fun newBrandJpaEntity(
        name: String = "Loopers",
        description: String = "감성 이커머스 브랜드",
        logoImageUrl: String? = null,
    ) = BrandJpaEntity(
        name = name,
        description = description,
        logoImageUrl = logoImageUrl,
    )

    private fun saveProductWithStock(
        brandId: Long,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        stock: Int = 10,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        productLikeCountQueryRepository.save(
            ProductLikeCountProjectionEntity(
                productId = product.id,
                brandId = brandId,
                likeCount = 0,
            ),
        )
        return product
    }
}
