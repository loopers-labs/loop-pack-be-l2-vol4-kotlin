package com.loopers.infrastructure.product

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.product.ProductSort
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal

@SpringBootTest
class ProductRedisCacheRepositoryTest @Autowired constructor(
    private val productCacheRepository: ProductCacheRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("상품 상세 캐시는 miss 후 저장하면 hit 되고 TTL과 evict가 동작한다.")
    @Test
    fun cachesProductDetail() {
        // arrange
        val product = productInfo(id = 1L, name = "Air Max")
        val key = ProductRedisCacheRepository.detailKey(1L)

        // act
        val miss = productCacheRepository.getDetail(1L)
        productCacheRepository.putDetail(productId = 1L, product = product)
        val hit = productCacheRepository.getDetail(1L)
        val ttl = redisTemplate.getExpire(key)
        productCacheRepository.evictDetail(1L)

        // assert
        assertAll(
            { assertThat(miss).isNull() },
            { assertThat(hit).isEqualTo(product) },
            { assertThat(ttl).isBetween(1L, ProductRedisCacheRepository.DETAIL_TTL.seconds) },
            { assertThat(productCacheRepository.getDetail(1L)).isNull() },
        )
    }

    @DisplayName("상품 목록 캐시는 쿼리별 키로 저장되고 30초 TTL을 가진다.")
    @Test
    fun cachesProductList() {
        // arrange
        val query = ProductCacheRepository.ProductListCacheQuery(
            brandId = null,
            sort = ProductSort.LIKES_DESC,
            page = 0,
            size = 20,
        )
        val products = ProductPageInfo(
            items = listOf(productInfo(id = 1L, name = "Air Max")),
            page = 0,
            size = 20,
            totalCount = 1,
            totalPages = 1,
        )
        val key = ProductRedisCacheRepository.listKey(query)

        // act
        val miss = productCacheRepository.getList(query)
        productCacheRepository.putList(query, products)
        val hit = productCacheRepository.getList(query)
        val ttl = redisTemplate.getExpire(key)

        // assert
        assertAll(
            { assertThat(miss).isNull() },
            { assertThat(hit).isEqualTo(products) },
            { assertThat(key).isEqualTo("commerce-api:product:list:v1:brand:all:sort:LIKES_DESC:page:0:size:20") },
            { assertThat(ttl).isBetween(1L, ProductRedisCacheRepository.LIST_TTL.seconds) },
        )
    }

    private fun productInfo(id: Long, name: String): ProductInfo {
        return ProductInfo(
            id = id,
            brand = ProductInfo.Brand(
                id = 10L,
                name = "Nike",
                description = "Shoes",
            ),
            name = name,
            description = "Description",
            price = BigDecimal("10000.00"),
            stockQuantity = 5,
            likeCount = 3,
        )
    }
}
