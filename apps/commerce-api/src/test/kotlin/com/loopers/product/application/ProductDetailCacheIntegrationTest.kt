package com.loopers.product.application

import com.loopers.brand.domain.Brand
import com.loopers.brand.domain.BrandName
import com.loopers.brand.domain.BrandRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.utils.RedisCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.clearMocks
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.AopTestUtils

@SpringBootTest
@ActiveProfiles("test")
class ProductDetailCacheIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val brandRepository: BrandRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
    private val databaseCleanup: DatabaseCleanup,
) {
    @SpykBean
    private lateinit var productRepository: ProductRepository

    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
        redisCleanUp.truncateAll()
        clearMocks(
            AopTestUtils.getUltimateTargetObject(productRepository),
            answers = false,
            childMocks = false,
            exclusionRules = false,
        )
    }

    @AfterEach
    fun tearDown() {
        circuitBreakerRegistry.circuitBreaker("order-queue").reset()
    }

    @DisplayName("같은 상품을 두 번 조회하면 두 번째는 캐시에서 응답하고 DB 를 조회하지 않는다.")
    @Test
    fun servesFromCache_onSecondRead() {
        val product = seedProduct()

        val first = productService.getDetail(product.id)
        val second = productService.getDetail(product.id)

        assertAll(
            { assertThat(second).isEqualTo(first) },
            { verify(exactly = 1) { productRepository.findActiveById(product.id) } },
        )
    }

    @DisplayName("상세 캐시는 productDetail 네임스페이스 키로 저장되고 TTL 1분이 걸린다.")
    @Test
    fun storesEntry_withNamespacedKeyAndTtl() {
        val product = seedProduct()

        productService.getDetail(product.id)

        val cacheKey = "productDetail::${product.id}"
        assertAll(
            { assertThat(redisTemplate.hasKey(cacheKey)).isTrue() },
            { assertThat(redisTemplate.getExpire(cacheKey)).isBetween(1L, 60L) },
        )
    }

    @DisplayName("대기열 서킷(order-queue)이 OPEN 이어도 상세 조회는 영향 없이 동작한다. (캐시는 별도 계약)")
    @Test
    fun keepsServingDetail_whileQueueCircuitIsOpen() {
        val product = seedProduct()
        circuitBreakerRegistry.circuitBreaker("order-queue").transitionToOpenState()

        val detail = productService.getDetail(product.id)

        assertThat(detail.name).isEqualTo("에어맥스")
    }

    private fun seedProduct(): Product {
        val brand = brandRepository.save(Brand(BrandName("나이키")))
        return productRepository.save(Product(brandId = brand.id, name = ProductName("에어맥스"), price = Money(100_000)))
    }
}
