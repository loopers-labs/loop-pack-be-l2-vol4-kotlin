package com.loopers.application.like

import com.loopers.application.product.ProductCacheStore
import com.loopers.application.product.ProductFacade
import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest
class LikeFacadeCacheTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productFacade: ProductFacade,
    private val productCacheStore: ProductCacheStore,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("좋아요를 등록하면, 해당 상품의 상세 캐시가 무효화된다.")
    @Test
    fun like_evictsDetailCache() {
        // arrange
        userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
        productFacade.getProductDetail(product.id) // 캐시 워밍
        assertThat(productCacheStore.getProductDetail(product.id)).isNotNull

        // act
        likeFacade.like("user0", rawPassword, product.id)

        // assert: 캐시 무효화는 커밋 후 비동기로 수행된다
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(productCacheStore.getProductDetail(product.id)).isNull()
        }
    }

    @DisplayName("좋아요를 취소하면, 해당 상품의 상세 캐시가 무효화된다.")
    @Test
    fun unlike_evictsDetailCache() {
        // arrange
        userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
        likeFacade.like("user0", rawPassword, product.id)
        productFacade.getProductDetail(product.id) // 캐시 워밍
        assertThat(productCacheStore.getProductDetail(product.id)).isNotNull

        // act
        likeFacade.unlike("user0", rawPassword, product.id)

        // assert: 캐시 무효화는 커밋 후 비동기로 수행된다
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(productCacheStore.getProductDetail(product.id)).isNull()
        }
    }
}
