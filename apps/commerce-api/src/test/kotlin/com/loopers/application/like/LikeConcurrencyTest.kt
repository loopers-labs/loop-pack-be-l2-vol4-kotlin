package com.loopers.application.like

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class LikeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val productJpaRepository: ProductJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요를 요청해도, 좋아요 수가 정확히 반영된다.")
    @Test
    fun likeCountIsAccurate_underConcurrency() {
        // arrange
        val userCount = 10
        repeat(userCount) { i ->
            userFacade.signUp("user$i", rawPassword, "유저$i", LocalDate.of(1994, 7, 14), "user$i@example.com")
        }
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 1_000, 10, ProductStatus.ON_SALE)
        val latch = CountDownLatch(userCount)
        val executor = Executors.newFixedThreadPool(userCount)

        // act: 10명이 동시에 좋아요
        repeat(userCount) { i ->
            executor.submit {
                try {
                    likeFacade.like("user$i", rawPassword, product.id)
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()

        // assert: 좋아요 수가 최종적으로 정확히 10 (집계는 커밋 후 비동기로 반영)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val reloaded = productJpaRepository.findById(product.id).orElseThrow()
            assertThat(reloaded.likeCount).isEqualTo(10L)
        }
    }
}
