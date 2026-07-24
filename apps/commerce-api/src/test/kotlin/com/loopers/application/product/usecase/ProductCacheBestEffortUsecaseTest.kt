package com.loopers.application.product.usecase

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.ranking.RankedProduct
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal

class ProductCacheBestEffortUsecaseTest {
    @DisplayName("상세 캐시 조회가 실패해도 DB 조회 결과를 반환한다.")
    @Test
    fun returnsProductDetail_whenDetailCacheGetFails() {
        // arrange
        val fixture = Fixture(cacheRepository = FailingProductCacheRepository(failOnGetDetail = true))

        // act
        val result = fixture.getProductDetailUsecase.execute(10L)

        // assert
        assertThat(result.id).isEqualTo(10L)
    }

    @DisplayName("상세 캐시 저장이 실패해도 DB 조회 결과를 반환한다.")
    @Test
    fun returnsProductDetail_whenDetailCachePutFails() {
        // arrange
        val fixture = Fixture(cacheRepository = FailingProductCacheRepository(failOnPutDetail = true))

        // act
        val result = fixture.getProductDetailUsecase.execute(10L)

        // assert
        assertThat(result.id).isEqualTo(10L)
    }

    @DisplayName("목록 캐시 조회가 실패해도 DB 조회 결과를 반환한다.")
    @Test
    fun returnsProductList_whenListCacheGetFails() {
        // arrange
        val fixture = Fixture(cacheRepository = FailingProductCacheRepository(failOnGetList = true))

        // act
        val result = fixture.getProductsUsecase.execute(GetProductsUsecase.Query(sort = ProductSort.LIKES_DESC))

        // assert
        assertThat(result.items.map { it.id }).containsExactly(10L)
    }

    @DisplayName("목록 캐시 저장이 실패해도 DB 조회 결과를 반환한다.")
    @Test
    fun returnsProductList_whenListCachePutFails() {
        // arrange
        val fixture = Fixture(cacheRepository = FailingProductCacheRepository(failOnPutList = true))

        // act
        val result = fixture.getProductsUsecase.execute(GetProductsUsecase.Query(sort = ProductSort.LIKES_DESC))

        // assert
        assertThat(result.items.map { it.id }).containsExactly(10L)
    }

    private class Fixture(
        cacheRepository: ProductCacheRepository,
    ) {
        private val productRepository = InMemoryProductRepository()
        private val brandRepository = InMemoryBrandRepository()
        private val productStockRepository = InMemoryProductStockRepository()

        val getProductDetailUsecase = GetProductDetailUsecase(
            productRepository = productRepository,
            brandRepository = brandRepository,
            productStockRepository = productStockRepository,
            productCacheRepository = cacheRepository,
            rankingQueryRepository = NoopRankingQueryRepository(),
            eventPublisher = ApplicationEventPublisher { },
        )
        val getProductsUsecase = GetProductsUsecase(
            productRepository = productRepository,
            brandRepository = brandRepository,
            productStockRepository = productStockRepository,
            productCacheRepository = cacheRepository,
        )

        init {
            brandRepository.save(BrandModel(name = "Nike", description = "Shoes").withId(1L))
            productRepository.save(
                ProductModel(
                    brandId = 1L,
                    name = "Air Max",
                    description = "Shoes",
                    price = BigDecimal("120000.00"),
                    likeCount = 10,
                ).withId(10L),
            )
            productStockRepository.save(ProductStockModel(productId = 10L, quantity = 5))
        }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val products = mutableMapOf<Long, ProductModel>()

        override fun save(product: ProductModel): ProductModel {
            products[product.id] = product
            return product
        }

        override fun findActiveById(id: Long): ProductModel? {
            return products[id]?.takeUnless { it.isDeleted() }
        }

        override fun findActiveAllByIds(ids: List<Long>): List<ProductModel> {
            return ids.mapNotNull { findActiveById(it) }
        }

        override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> {
            val content = products.values
                .filter { !it.isDeleted() }
                .filter { brandId == null || it.brandId == brandId }
                .sortedWith(compareByDescending<ProductModel> { it.likeCount }.thenByDescending { it.id })
            return PageImpl(content, pageable, content.size.toLong())
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }

        override fun incrementLikeCount(productId: Long) {
            findActiveById(productId)?.incrementLikeCount()
        }

        override fun decrementLikeCount(productId: Long) {
            findActiveById(productId)?.decrementLikeCount()
        }
    }

    private class InMemoryBrandRepository : BrandRepository {
        private val brands = mutableMapOf<Long, BrandModel>()

        override fun save(brand: BrandModel): BrandModel {
            brands[brand.id] = brand
            return brand
        }

        override fun findById(id: Long): BrandModel? {
            return brands[id]
        }

        override fun findActiveById(id: Long): BrandModel? {
            return brands[id]?.takeUnless { it.isDeleted() }
        }

        override fun findActiveAllByIds(ids: List<Long>): List<BrandModel> {
            return ids.mapNotNull { findActiveById(it) }
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }
    }

    private class InMemoryProductStockRepository : ProductStockRepository {
        private val stocks = mutableMapOf<Long, ProductStockModel>()

        override fun save(stock: ProductStockModel): ProductStockModel {
            stocks[stock.productId] = stock
            return stock
        }

        override fun findByProductId(productId: Long): ProductStockModel? {
            return stocks[productId]
        }

        override fun findByProductIdForUpdate(productId: Long): ProductStockModel? {
            return findByProductId(productId)
        }

        override fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel> {
            return productIds.mapNotNull { stocks[it] }
        }
    }

    private class FailingProductCacheRepository(
        private val failOnGetDetail: Boolean = false,
        private val failOnPutDetail: Boolean = false,
        private val failOnGetList: Boolean = false,
        private val failOnPutList: Boolean = false,
    ) : ProductCacheRepository {
        override fun getDetail(productId: Long): ProductInfo? {
            if (failOnGetDetail) throw IllegalStateException("detail cache get failed")
            return null
        }

        override fun putDetail(productId: Long, product: ProductInfo) {
            if (failOnPutDetail) throw IllegalStateException("detail cache put failed")
        }

        override fun evictDetail(productId: Long) = Unit

        override fun getList(query: ProductCacheRepository.ProductListCacheQuery): ProductPageInfo? {
            if (failOnGetList) throw IllegalStateException("list cache get failed")
            return null
        }

        override fun putList(query: ProductCacheRepository.ProductListCacheQuery, products: ProductPageInfo) {
            if (failOnPutList) throw IllegalStateException("list cache put failed")
        }
    }

    private class NoopRankingQueryRepository : RankingQueryRepository {
        override fun page(key: String, offset: Long, size: Long): List<RankedProduct> = emptyList()
        override fun total(key: String): Long = 0
        override fun rank(key: String, productId: Long): Long? = null
    }
}
