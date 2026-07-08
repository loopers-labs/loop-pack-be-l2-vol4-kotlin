package com.loopers.application.useraction

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.loopers.application.like.LikeFacade
import com.loopers.application.product.ProductFacade
import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.product.ProductViewedEvent
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest
@RecordApplicationEvents
class UserActionLogIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val likeFacade: LikeFacade,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val rawPassword = "Valid1!pw"
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUpLogAppender() {
        logAppender = ListAppender()
        logAppender.start()
        (LoggerFactory.getLogger(UserActionLogListener::class.java) as Logger).addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        (LoggerFactory.getLogger(UserActionLogListener::class.java) as Logger).detachAppender(logAppender)
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("상품 상세를 조회하면, ProductViewedEvent 가 발행된다.")
    @Test
    fun getProductDetail_publishesProductViewedEvent(events: ApplicationEvents) {
        // arrange
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        productFacade.getProductDetail(product.id)

        // assert
        val published = events.stream(ProductViewedEvent::class.java).toList()
        assertThat(published).containsExactly(ProductViewedEvent(productId = product.id))
    }

    @DisplayName("캐시에 적중한 조회도, ProductViewedEvent 가 발행된다.")
    @Test
    fun getProductDetail_publishesEvent_onCacheHit(events: ApplicationEvents) {
        // arrange
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
        productFacade.getProductDetail(product.id) // 캐시 워밍 (미스 조회)

        // act
        productFacade.getProductDetail(product.id) // 히트 조회

        // assert: 미스 1회 + 히트 1회 = 2회 발행
        val published = events.stream(ProductViewedEvent::class.java).toList()
        assertThat(published).hasSize(2)
    }

    @DisplayName("상품 조회 행동이, 서버 로그로 기록된다.")
    @Test
    fun productView_isLogged() {
        // arrange
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        productFacade.getProductDetail(product.id)

        // assert: 로깅은 비동기로 수행된다
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(logAppender.list.map { it.formattedMessage })
                .anyMatch { it.contains("PRODUCT_VIEWED") && it.contains("productId=${product.id}") }
        }
    }

    @DisplayName("좋아요 행동이, 서버 로그로 기록된다.")
    @Test
    fun productLike_isLogged() {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        likeFacade.like("user0", rawPassword, product.id)

        // assert: 로깅은 비동기로 수행된다
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(logAppender.list.map { it.formattedMessage })
                .anyMatch { it.contains("PRODUCT_LIKED") && it.contains("userId=${user.id}") && it.contains("productId=${product.id}") }
        }
    }
}
