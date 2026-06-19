package com.loopers.application.like

import com.loopers.application.product.ProductApplicationService
import com.loopers.domain.user.EncodedPassword
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class LikeFacadeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productApplicationService: ProductApplicationService,
    private val productJpaRepository: ProductJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 동시성 제어 시, ")
    @Nested
    inner class LikeConcurrently {
        @DisplayName("여러 사용자가 동일 상품에 동시에 좋아요를 요청해도 좋아요 수가 정확히 집계된다.")
        @Test
        fun aggregatesLikeCountCorrectly_whenDifferentUsersLikeSameProductConcurrently() {
            // arrange
            val users = (1..10).map { index ->
                userJpaRepository.save(
                    newUserJpaEntity(loginId = "user$index", email = "user$index@example.com"),
                )
            }
            val product = productJpaRepository.save(newProductJpaEntity(likeCount = 0))

            // act
            val results = runConcurrently(users.size) { index ->
                likeFacade.addLike(userId = users[index].id, productId = product.id)
            }

            // assert
            val updatedProduct = productApplicationService.getProduct(product.id)
            assertAll(
                { assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() } },
                { assertThat(updatedProduct.likeCount).isEqualTo(users.size) },
                { assertThat(likeJpaRepository.findAll()).hasSize(users.size) },
            )
        }

        @DisplayName("여러 사용자가 동일 상품의 좋아요를 동시에 취소해도 좋아요 수가 정확히 집계된다.")
        @Test
        fun aggregatesLikeCountCorrectly_whenDifferentUsersCancelSameProductConcurrently() {
            // arrange
            val users = (1..10).map { index ->
                userJpaRepository.save(
                    newUserJpaEntity(loginId = "user$index", email = "user$index@example.com"),
                )
            }
            val product = productJpaRepository.save(newProductJpaEntity(likeCount = 0))
            users.forEach { likeFacade.addLike(userId = it.id, productId = product.id) }

            // act
            val results = runConcurrently(users.size) { index ->
                likeFacade.cancelLike(userId = users[index].id, productId = product.id)
            }

            // assert
            val updatedProduct = productApplicationService.getProduct(product.id)
            assertAll(
                { assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() } },
                { assertThat(updatedProduct.likeCount).isEqualTo(0) },
                { assertThat(likeJpaRepository.findAll()).allSatisfy { assertThat(it.deletedAt).isNotNull() } },
            )
        }
    }

    private fun <T> runConcurrently(
        times: Int,
        task: (Int) -> T,
    ): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)

        return try {
            val futures = (0 until times).map { index ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task(index) }
                    },
                )
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun newUserJpaEntity(
        loginId: String = "seondays",
        password: String = "\$2a\$10\$existingHashedPassword.",
        name: String = "선데이",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "seondays@example.com",
    ) = UserJpaEntity(
        loginId = loginId,
        encodedPassword = EncodedPassword(password),
        name = name,
        birthDate = birthDate,
        email = email,
    )

    private fun newProductJpaEntity(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        likeCount: Int = 0,
    ) = ProductJpaEntity(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        likeCount = likeCount,
    )
}
