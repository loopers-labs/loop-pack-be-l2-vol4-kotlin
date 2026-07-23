package com.loopers.application.product

import com.loopers.application.brand.BrandService
import com.loopers.application.inventory.InventoryService
import com.loopers.application.product.cache.ProductCacheRepository
import com.loopers.application.product.cache.ProductCacheService
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductCreateCommand
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
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.LocalDate

class ProductFacadeAdminTest {
    @DisplayName("관리자 상품 삭제")
    @Nested
    inner class DeleteProduct {
        @DisplayName("등록된 상품을 삭제한다")
        @Test
        fun deletesProduct() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            fixture.productFacade.deleteProduct(10L)

            val result = fixture.productRepository.products.find { it.id == 10L }
            assertThat(result?.isDeleted).isTrue()
        }

        @DisplayName("존재하지 않는 상품은 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = ProductFacadeAdminFixture()

            val result = assertThrows<CoreException> {
                fixture.productFacade.deleteProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 상품은 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.productFacade.deleteProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.productFacade.deleteProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 수정")
    @Nested
    inner class UpdateProduct {
        @DisplayName("등록된 상품 기본 정보를 수정한다")
        @Test
        fun updatesProduct() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.inventoryRepository.save(Inventory(productId = 10L, quantity = 7L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, brandId = 1L, likeCount = 5L))

            val result = fixture.productFacade.updateProduct(
                productId = 10L,
                command = ProductUpdateCommand(
                    name = "updated hoodie",
                    price = 20_000L,
                    description = "updated product",
                    imageUrl = "https://image.loopers/updated.png",
                ),
            )

            assertAll(
                { assertThat(result.productId).isEqualTo(10L) },
                { assertThat(result.productName).isEqualTo("updated hoodie") },
                { assertThat(result.price).isEqualTo(20_000L) },
                { assertThat(result.brand.brandId).isEqualTo(1L) },
                { assertThat(result.likeCount).isEqualTo(5L) },
                { assertThat(result.quantity).isEqualTo(7L) },
            )
        }

        @DisplayName("같은 브랜드 내 다른 상품 이름과 중복되게 수정할 수 없다")
        @Test
        fun throwsConflict_whenProductNameAlreadyExistsInBrand() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productRepository.save(createProduct(id = 20L, brandId = 1L, name = "updated hoodie"))
            fixture.inventoryRepository.save(Inventory(productId = 10L, quantity = 7L))

            val result = assertThrows<CoreException> {
                fixture.productFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("존재하지 않는 상품은 수정할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = ProductFacadeAdminFixture()

            val result = assertThrows<CoreException> {
                fixture.productFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 수정할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.productFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 수정할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.productFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 등록")
    @Nested
    inner class CreateProduct {
        @DisplayName("기존 브랜드에 상품을 등록한다")
        @Test
        fun createsProduct() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))

            val result = fixture.productFacade.createProduct(
                ProductCreateCommand(
                    brandId = 1L,
                    name = "loopers hoodie",
                    price = 10_000L,
                    description = "loopers product",
                    imageUrl = "https://image.loopers/product.png",
                    quantity = 100L,
                ),
            )

            assertAll(
                { assertThat(result.productId).isEqualTo(1L) },
                { assertThat(result.productName).isEqualTo("loopers hoodie") },
                { assertThat(result.brand.brandId).isEqualTo(1L) },
                { assertThat(result.likeCount).isEqualTo(0L) },
                { assertThat(result.quantity).isEqualTo(100L) },
                { assertThat(fixture.inventoryRepository.findByProductId(result.productId)?.quantity).isEqualTo(100L) },
            )
        }

        @DisplayName("같은 브랜드에 같은 이름의 상품은 등록할 수 없다")
        @Test
        fun throwsConflict_whenProductNameAlreadyExistsInBrand() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.productFacade.createProduct(
                    ProductCreateCommand(
                        brandId = 1L,
                        name = "loopers hoodie",
                        price = 10_000L,
                        description = "loopers product",
                        imageUrl = "https://image.loopers/product.png",
                        quantity = 100L,
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("존재하지 않는 브랜드에는 상품을 등록할 수 없다")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            val fixture = ProductFacadeAdminFixture()

            val result = assertThrows<CoreException> {
                fixture.productFacade.createProduct(
                    ProductCreateCommand(
                        brandId = 1L,
                        name = "loopers hoodie",
                        price = 10_000L,
                        description = "loopers product",
                        imageUrl = "https://image.loopers/product.png",
                        quantity = 100L,
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드에는 상품을 등록할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.productFacade.createProduct(
                    ProductCreateCommand(
                        brandId = 1L,
                        name = "loopers hoodie",
                        price = 10_000L,
                        description = "loopers product",
                        imageUrl = "https://image.loopers/product.png",
                        quantity = 100L,
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 상세 조회")
    @Nested
    inner class GetProduct {
        @DisplayName("등록된 상품 상세 정보를 조회한다")
        @Test
        fun returnsProductDetail() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.inventoryRepository.save(Inventory(productId = 10L, quantity = 7L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, brandId = 1L, likeCount = 3L))

            val result = fixture.productFacade.getProductForAdmin(10L)

            assertAll(
                { assertThat(result.productId).isEqualTo(10L) },
                { assertThat(result.brand.name).isEqualTo("loopers") },
                { assertThat(result.likeCount).isEqualTo(3L) },
                { assertThat(result.quantity).isEqualTo(7L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 조회할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = ProductFacadeAdminFixture()

            val result = assertThrows<CoreException> {
                fixture.productFacade.getProductForAdmin(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 조회할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.productFacade.getProductForAdmin(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 조회할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = ProductFacadeAdminFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.productFacade.getProductForAdmin(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 목록 조회")
    @Nested
    inner class GetProducts {
        @DisplayName("등록된 상품 목록을 페이지로 조회한다")
        @Test
        fun returnsProductPage() {
            val fixture = ProductFacadeAdminFixture()
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productRepository.save(createProduct(id = 20L, brandId = 2L))

            val result = fixture.productFacade.getProducts(
                ProductListCommand(
                    brandId = null,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(result.content.map { it.productId }).containsExactly(20L, 10L) },
            )
        }

        @DisplayName("브랜드 ID가 주어지면 해당 브랜드 상품만 조회한다")
        @Test
        fun returnsProductPageFilteredByBrandId() {
            val fixture = ProductFacadeAdminFixture()
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productRepository.save(createProduct(id = 20L, brandId = 2L))

            val result = fixture.productFacade.getProducts(
                ProductListCommand(
                    brandId = 1L,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content[0].brandId).isEqualTo(1L) },
            )
        }
    }

    private class ProductFacadeAdminFixture {
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
        override fun findPage(
            period: RankingPeriod,
            date: LocalDate,
            page: Int,
            size: Int,
        ): RankingPage = RankingPage(emptyList(), 0L)

        override fun findRank(date: LocalDate, productId: Long): Long? = null
    }

    private class FakeProductEventPublisher : ProductEventPublisher {
        override fun publish(event: ProductEvent.Viewed) = Unit
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
            return PageImpl(emptyList(), PageRequest.of(page, size), 0)
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
        val products = mutableListOf<Product>()
        private var sequence = 1L

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
            val content = products
                .filter { !it.isDeleted }
                .filter { brandId == null || it.brandId == brandId }
                .sortedWith(compareByDescending<Product> { it.id })
                .drop(page * size)
                .take(size)
                .map {
                    ProductSummary(
                        productId = it.id,
                        productName = it.name,
                        price = it.price,
                        imageUrl = it.imageUrl,
                        brandId = it.brandId,
                        brandName = "brand-${it.brandId}",
                        likeCount = 0L,
                    )
                }

            val total = products
                .filter { !it.isDeleted }
                .count { brandId == null || it.brandId == brandId }

            return PageImpl(
                content,
                PageRequest.of(page, size),
                total.toLong(),
            )
        }

        override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean {
            return products.any { it.brandId == brandId && it.name == name }
        }

        override fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean {
            return products.any { it.brandId == brandId && it.name == name && it.id != productId }
        }

        override fun save(product: Product): Product {
            val saved = if (product.id == 0L) {
                Product(
                    id = sequence++,
                    brandId = product.brandId,
                    name = product.name,
                    price = product.price,
                    description = product.description,
                    imageUrl = product.imageUrl,
                    isDeleted = product.isDeleted,
                )
            } else {
                product
            }
            products.removeIf { it.id == saved.id }
            products.add(saved)
            return saved
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

        override fun findDetail(productId: Long): ProductDetailInfo? {
            return details[productId]
        }

        override fun saveDetail(productId: Long, productDetail: ProductDetailInfo) {
            details[productId] = productDetail
        }

        override fun evictDetail(productId: Long) {
            details.remove(productId)
        }

        override fun findList(command: ProductListCommand): Page<ProductSummary>? {
            return lists[command]
        }

        override fun saveList(command: ProductListCommand, productSummaries: Page<ProductSummary>) {
            lists[command] = productSummaries
        }

        override fun acquireListRefreshLock(command: ProductListCommand): Boolean {
            return locks.add(command)
        }

        override fun releaseListRefreshLock(command: ProductListCommand) {
            locks.remove(command)
        }
    }

    private fun createBrand(
        id: Long,
        name: String,
        isDeleted: Boolean = false,
    ): Brand {
        return Brand(
            id = id,
            name = name,
            description = "$name brand",
            logoImageUrl = "https://image.loopers/$name.png",
            isDeleted = isDeleted,
        )
    }

    private fun createProduct(
        id: Long,
        brandId: Long,
        name: String = "loopers hoodie",
        isDeleted: Boolean = false,
    ): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = name,
            price = 10_000L,
            description = "loopers product",
            imageUrl = "https://image.loopers/product.png",
            isDeleted = isDeleted,
        )
    }
}
