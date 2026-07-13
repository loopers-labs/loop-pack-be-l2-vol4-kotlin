package com.loopers.application.product

import com.loopers.application.brand.BrandService
import com.loopers.application.brand.dto.BrandInfo
import com.loopers.application.inventory.InventoryService
import com.loopers.application.product.cache.ProductCacheRepository
import com.loopers.application.product.cache.ProductCacheService
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.application.product.dto.ProductUpdateCommand
import com.loopers.application.productstat.ProductStatService
import com.loopers.application.ranking.RankingQueryService
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.brand.model.Brand
import com.loopers.domain.brand.repository.BrandRepository
import com.loopers.domain.inventory.model.Inventory
import com.loopers.domain.inventory.repository.InventoryRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.product.model.Product
import com.loopers.domain.product.model.ProductStat
import com.loopers.domain.product.event.ProductEvent
import com.loopers.domain.product.event.ProductEventPublisher
import com.loopers.domain.product.repository.ProductRepository
import com.loopers.domain.product.repository.ProductStatRepository
import com.loopers.domain.product.service.ProductCatalogService
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingRepository
import com.loopers.fixture.product.ProductBrandFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.LocalDate

class ProductFacadeTest {
    @DisplayName("상품 목록 조회")
    @Nested
    inner class GetProducts {
        @DisplayName("상품 목록에 브랜드명과 좋아요 수를 포함한다")
        @Test
        fun returnsProductSummariesWithBrandAndLikeCount() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productStatRepository.save(ProductBrandFixture.createProductStat(productId = 10L, likeCount = 3L))

            val result = fixture.productFacade.getProducts(
                ProductListCommand(
                    brandId = null,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content[0].brandName).isEqualTo("loopers") },
                { assertThat(result.content[0].likeCount).isEqualTo(3L) },
            )
        }

        @DisplayName("목록 캐시가 있으면 캐시된 값을 반환하고 비동기 갱신을 요청한다")
        @Test
        fun returnsCachedProductsAndRequestsRefresh() {
            val fixture = ProductServiceFixture()
            val command = ProductListCommand(
                brandId = 1L,
                sort = ProductSort.LIKES_DESC,
                page = 0,
                size = 20,
            )
            fixture.productCacheRepository.saveList(
                command,
                PageImpl(
                    listOf(
                        ProductSummary(
                            productId = 10L,
                            productName = "cached hoodie",
                            price = 10_000L,
                            imageUrl = "https://image.loopers/cached.png",
                            brandId = 1L,
                            brandName = "loopers",
                            likeCount = 99L,
                        ),
                    ),
                    PageRequest.of(0, 20),
                    1L,
                ),
            )
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 20L, brandId = 1L, name = "fresh hoodie"))

            val result = fixture.productFacade.getProducts(command)
            val refreshedProducts = fixture.productCacheRepository.findList(command)

            assertAll(
                { assertThat(result.content.first().productName).isEqualTo("cached hoodie") },
                { assertThat(refreshedProducts?.content?.first()?.productName).isEqualTo("fresh hoodie") },
            )
        }

        @DisplayName("목록 캐시가 없으면 DB 조회 결과를 캐시에 저장한다")
        @Test
        fun savesProductsOnCacheMiss() {
            val fixture = ProductServiceFixture()
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            val command = ProductListCommand(
                brandId = null,
                sort = ProductSort.LATEST,
                page = 0,
                size = 20,
            )

            fixture.productFacade.getProducts(command)

            val cachedProducts = fixture.productCacheRepository.findList(command)
            assertAll(
                { assertThat(cachedProducts?.content).hasSize(1) },
                { assertThat(cachedProducts?.content?.first()?.productId).isEqualTo(10L) },
            )
        }

        @DisplayName("상품 상세 조회에 성공하면 조회 이벤트를 발행한다")
        @Test
        fun publishesViewedEvent() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))

            fixture.productFacade.getProduct(10L)

            val event = fixture.productEventPublisher.events.single()
            assertAll(
                { assertThat(event.productId).isEqualTo(10L) },
                { assertThat(event.brandId).isEqualTo(1L) },
            )
        }

        @DisplayName("목록 캐시 조회에 실패해도 DB로 상품 목록을 조회한다")
        @Test
        fun returnsProductsFromDatabase_whenListCacheReadFails() {
            val fixture = ProductServiceFixture()
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productCacheRepository.throwOnFindList = true

            val result = fixture.productFacade.getProducts(
                ProductListCommand(
                    brandId = null,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertThat(result.content).hasSize(1)
        }

        @DisplayName("목록 캐시 저장에 실패해도 DB 조회 결과를 반환한다")
        @Test
        fun returnsProductsFromDatabase_whenListCacheSaveFails() {
            val fixture = ProductServiceFixture()
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productCacheRepository.throwOnSaveList = true

            val result = fixture.productFacade.getProducts(
                ProductListCommand(
                    brandId = null,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertThat(result.content).hasSize(1)
        }

        @DisplayName("목록 캐시 refresh에 실패해도 캐시된 상품 목록을 반환한다")
        @Test
        fun returnsCachedProducts_whenListCacheRefreshFails() {
            val fixture = ProductServiceFixture()
            val command = ProductListCommand(
                brandId = 1L,
                sort = ProductSort.LIKES_DESC,
                page = 0,
                size = 20,
            )
            fixture.productCacheRepository.saveList(
                command,
                PageImpl(
                    listOf(
                        ProductSummary(
                            productId = 10L,
                            productName = "cached hoodie",
                            price = 10_000L,
                            imageUrl = "https://image.loopers/cached.png",
                            brandId = 1L,
                            brandName = "loopers",
                            likeCount = 99L,
                        ),
                    ),
                    PageRequest.of(0, 20),
                    1L,
                ),
            )
            fixture.productCacheRepository.throwOnSaveList = true

            val result = fixture.productFacade.getProducts(command)

            assertThat(result.content.first().productName).isEqualTo("cached hoodie")
        }
    }

    @DisplayName("상품 상세 조회")
    @Nested
    inner class GetProduct {
        @DisplayName("상품 상세에 브랜드와 좋아요 수를 포함한다")
        @Test
        fun returnsProductDetailWithBrandAndLikeCount() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productStatRepository.save(ProductBrandFixture.createProductStat(productId = 10L, likeCount = 3L))

            val result = fixture.productFacade.getProduct(10L)

            assertAll(
                { assertThat(result.brand.name).isEqualTo("loopers") },
                { assertThat(result.likeCount).isEqualTo(3L) },
            )
        }

        @DisplayName("상세 캐시가 있으면 캐시된 상품 상세를 반환한다")
        @Test
        fun returnsCachedProductDetail() {
            val fixture = ProductServiceFixture()
            fixture.productCacheRepository.saveDetail(
                productId = 10L,
                productDetail = ProductDetailInfo(
                    productId = 10L,
                    productName = "cached hoodie",
                    price = 10_000L,
                    description = "cached product",
                    imageUrl = "https://image.loopers/cached.png",
                    brand = BrandInfo(
                        brandId = 1L,
                        name = "loopers",
                        description = "loopers brand",
                        logoImageUrl = "https://image.loopers/brand.png",
                    ),
                    likeCount = 10L,
                ),
            )

            val result = fixture.productFacade.getProduct(10L)

            assertThat(result.productName).isEqualTo("cached hoodie")
        }

        @DisplayName("상세 캐시가 없으면 DB 조회 결과를 캐시에 저장한다")
        @Test
        fun savesProductDetailOnCacheMiss() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))

            fixture.productFacade.getProduct(10L)

            val cachedDetail = fixture.productCacheRepository.findDetail(10L)
            assertThat(cachedDetail?.productId).isEqualTo(10L)
        }

        @DisplayName("상세 캐시 조회에 실패해도 DB로 상품 상세를 조회한다")
        @Test
        fun returnsProductDetailFromDatabase_whenDetailCacheReadFails() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productCacheRepository.throwOnFindDetail = true

            val result = fixture.productFacade.getProduct(10L)

            assertThat(result.productId).isEqualTo(10L)
        }

        @DisplayName("상세 캐시 저장에 실패해도 DB 조회 결과를 반환한다")
        @Test
        fun returnsProductDetailFromDatabase_whenDetailCacheSaveFails() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productCacheRepository.throwOnSaveDetail = true

            val result = fixture.productFacade.getProduct(10L)

            assertThat(result.productId).isEqualTo(10L)
        }
    }

    @DisplayName("상품 수정")
    @Nested
    inner class UpdateProduct {
        @DisplayName("상품 수정 후 상세 캐시를 무효화한다")
        @Test
        fun evictsProductDetailCache() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.inventoryRepository.save(Inventory(productId = 10L, quantity = 7L))
            fixture.productCacheRepository.saveDetail(
                productId = 10L,
                productDetail = ProductDetailInfo(
                    productId = 10L,
                    productName = "cached hoodie",
                    price = 10_000L,
                    description = "cached product",
                    imageUrl = "https://image.loopers/cached.png",
                    brand = BrandInfo(
                        brandId = 1L,
                        name = "loopers",
                        description = "loopers brand",
                        logoImageUrl = "https://image.loopers/brand.png",
                    ),
                    likeCount = 10L,
                ),
            )

            fixture.productFacade.updateProduct(
                productId = 10L,
                command = ProductUpdateCommand(
                    name = "updated hoodie",
                    price = 20_000L,
                    description = "updated product",
                    imageUrl = "https://image.loopers/updated.png",
                ),
            )

            assertThat(fixture.productCacheRepository.findDetail(10L)).isNull()
        }
    }

    private class ProductServiceFixture {
        val brandRepository = FakeBrandRepository()
        val inventoryRepository = FakeInventoryRepository()
        val productRepository = FakeProductRepository()
        val productStatRepository = FakeProductStatRepository()
        val productCacheRepository = FakeProductCacheRepository()
        val productEventPublisher = FakeProductEventPublisher()
        val productService = ProductService(productRepository)
        val productCacheService = ProductCacheService(
            productService = productService,
            productCacheRepository = productCacheRepository,
            taskExecutor = SyncTaskExecutor(),
        )
        val productFacade = ProductFacade(
            productService = productService,
            brandService = BrandService(brandRepository),
            inventoryService = InventoryService(inventoryRepository),
            productStatService = ProductStatService(productStatRepository),
            productCatalogService = ProductCatalogService(),
            productCacheService = productCacheService,
            productEventPublisher = productEventPublisher,
            rankingQueryService = RankingQueryService(
                rankingRepository = EmptyRankingRepository(),
                meterRegistry = SimpleMeterRegistry(),
                properties = RankingRedisProperties(),
                clock = Clock.systemUTC(),
            ),
        )
    }

    private class EmptyRankingRepository : RankingRepository {
        override fun findPage(date: LocalDate, page: Int, size: Int): RankingPage = RankingPage(emptyList(), 0L)

        override fun findRank(date: LocalDate, productId: Long): Long? = null
    }

    private class FakeProductEventPublisher : ProductEventPublisher {
        val events = mutableListOf<ProductEvent.Viewed>()

        override fun publish(event: ProductEvent.Viewed) {
            events.add(event)
        }
    }

    private class FakeBrandRepository : BrandRepository {
        private val brands = mutableListOf<Brand>()

        override fun findById(brandId: Long): Brand? {
            return brands.find { it.id == brandId && !it.isDeleted }
        }

        override fun findAllByIds(brandIds: Collection<Long>): List<Brand> {
            return brands.filter { it.id in brandIds && !it.isDeleted }
        }

        override fun findDisplayable(page: Int, size: Int): Page<Brand> {
            val content = brands
                .filter { !it.isDeleted }
                .drop(page * size)
                .take(size)

            return PageImpl(
                content,
                PageRequest.of(page, size),
                brands.count { !it.isDeleted }.toLong(),
            )
        }

        override fun existsByName(name: String): Boolean {
            return brands.any { it.name == name }
        }

        override fun save(brand: Brand): Brand {
            brands.removeIf { it.id == brand.id }
            brands.add(brand)
            return brand
        }

        override fun update(brand: Brand): Brand {
            brands.removeIf { it.id == brand.id }
            brands.add(brand)
            return brand
        }
    }

    private class FakeProductRepository : ProductRepository {
        private val products = mutableListOf<Product>()

        override fun findById(productId: Long): Product? {
            return products.find { it.id == productId && !it.isDeleted }
        }

        override fun findAllByIds(productIds: Collection<Long>): List<Product> {
            return products.filter { it.id in productIds && !it.isDeleted }
        }

        override fun findAllByBrandId(brandId: Long): List<Product> {
            return products.filter { it.brandId == brandId && !it.isDeleted }
        }

        override fun findDisplayableSummaries(
            brandId: Long?,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): Page<ProductSummary> {
            val items = products
                .filter { !it.isDeleted }
                .filter { brandId == null || it.brandId == brandId }
                .drop(page * size)
                .take(size)
                .map {
                    ProductSummary(
                        productId = it.id,
                        productName = it.name,
                        price = it.price,
                        imageUrl = it.imageUrl,
                        brandId = it.brandId,
                        brandName = "loopers",
                        likeCount = 3L,
                    )
                }

            return PageImpl(
                items,
                PageRequest.of(page, size),
                products.size.toLong(),
            )
        }

        override fun save(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean {
            return products.any { it.brandId == brandId && it.name == name }
        }

        override fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean {
            return products.any { it.brandId == brandId && it.name == name && it.id != productId }
        }

        override fun update(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun updateAll(products: Collection<Product>): List<Product> {
            products.forEach(::save)
            return products.toList()
        }
    }

    private class FakeInventoryRepository : InventoryRepository {
        private val inventories = mutableListOf<Inventory>()

        override fun findByProductId(productId: Long): Inventory? {
            return inventories.find { it.productId == productId }
        }

        override fun findAllByProductIdsForUpdate(productIds: Collection<Long>): List<Inventory> {
            return inventories.filter { it.productId in productIds }
        }

        override fun save(inventory: Inventory): Inventory {
            inventories.removeIf { it.productId == inventory.productId }
            inventories.add(inventory)
            return inventory
        }

        override fun updateAll(inventories: Collection<Inventory>): List<Inventory> {
            inventories.forEach(::save)
            return inventories.toList()
        }
    }

    private class FakeProductStatRepository : ProductStatRepository {
        private val productStats = mutableListOf<ProductStat>()

        override fun findByProductId(productId: Long): ProductStat? {
            return productStats.find { it.productId == productId }
        }

        override fun findByProductIdForUpdate(productId: Long): ProductStat? {
            return findByProductId(productId)
        }

        override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat> {
            return productStats.filter { it.productId in productIds }
        }

        override fun save(productStat: ProductStat): ProductStat {
            productStats.removeIf { it.productId == productStat.productId }
            productStats.add(productStat)
            return productStat
        }
    }

    private class FakeProductCacheRepository : ProductCacheRepository {
        private val details = mutableMapOf<Long, ProductDetailInfo>()
        private val lists = mutableMapOf<ProductListCommand, Page<ProductSummary>>()
        private val locks = mutableSetOf<ProductListCommand>()
        var throwOnFindDetail = false
        var throwOnSaveDetail = false
        var throwOnFindList = false
        var throwOnSaveList = false

        override fun findDetail(productId: Long): ProductDetailInfo? {
            if (throwOnFindDetail) {
                throw IllegalStateException("detail cache read failed")
            }

            return details[productId]
        }

        override fun saveDetail(productId: Long, productDetail: ProductDetailInfo) {
            if (throwOnSaveDetail) {
                throw IllegalStateException("detail cache save failed")
            }

            details[productId] = productDetail
        }

        override fun evictDetail(productId: Long) {
            details.remove(productId)
        }

        override fun findList(command: ProductListCommand): Page<ProductSummary>? {
            if (throwOnFindList) {
                throw IllegalStateException("list cache read failed")
            }

            return lists[command]
        }

        override fun saveList(command: ProductListCommand, productSummaries: Page<ProductSummary>) {
            if (throwOnSaveList) {
                throw IllegalStateException("list cache save failed")
            }

            lists[command] = productSummaries
        }

        override fun acquireListRefreshLock(command: ProductListCommand): Boolean {
            return locks.add(command)
        }

        override fun releaseListRefreshLock(command: ProductListCommand) {
            locks.remove(command)
        }
    }
}
