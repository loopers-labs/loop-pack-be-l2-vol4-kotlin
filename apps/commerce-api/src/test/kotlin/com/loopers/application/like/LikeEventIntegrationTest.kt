package com.loopers.application.like

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.ProductLikedEvent
import com.loopers.domain.like.ProductUnlikedEvent
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest
@RecordApplicationEvents
class LikeEventIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val likeRepository: LikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @SpykBean
    private lateinit var productService: ProductService

    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("좋아요를 등록하면, ProductLikedEvent 가 발행된다.")
    @Test
    fun like_publishesProductLikedEvent(events: ApplicationEvents) {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        likeFacade.like("user0", rawPassword, product.id)

        // assert
        val published = events.stream(ProductLikedEvent::class.java).toList()
        assertThat(published).containsExactly(ProductLikedEvent(userId = user.id, productId = product.id))
    }

    @DisplayName("좋아요를 취소하면, ProductUnlikedEvent 가 발행된다.")
    @Test
    fun unlike_publishesProductUnlikedEvent(events: ApplicationEvents) {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
        likeFacade.like("user0", rawPassword, product.id)

        // act
        likeFacade.unlike("user0", rawPassword, product.id)

        // assert
        val published = events.stream(ProductUnlikedEvent::class.java).toList()
        assertThat(published).containsExactly(ProductUnlikedEvent(userId = user.id, productId = product.id))
    }

    @DisplayName("좋아요 집계 처리가 실패해도, 좋아요 등록은 성공한다.")
    @Test
    fun like_succeedsEvenIfAggregationFails() {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
        every { productService.increaseLikeCount(any()) } throws RuntimeException("집계 실패")

        // act
        likeFacade.like("user0", rawPassword, product.id)

        // assert
        assertThat(likeRepository.existsByUserIdAndProductId(user.id, product.id)).isTrue()
    }

    @DisplayName("좋아요를 등록하면, 상품의 좋아요 수가 최종적으로 반영된다.")
    @Test
    fun like_eventuallyIncreasesLikeCount() {
        // arrange
        userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        likeFacade.like("user0", rawPassword, product.id)

        // assert
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            val updated = productService.getActiveById(product.id)
            assertThat(updated.likeCount).isEqualTo(1L)
        }
    }
}
