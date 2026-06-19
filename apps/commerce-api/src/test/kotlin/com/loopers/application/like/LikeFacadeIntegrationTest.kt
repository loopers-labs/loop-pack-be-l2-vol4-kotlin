package com.loopers.application.like

import com.loopers.domain.user.EncodedPassword
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productJpaRepository: ProductJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    inner class AddLike {
        @DisplayName("상품이 존재하고 기존 좋아요가 없으면 좋아요를 등록한다.")
        @Test
        fun addLike_whenLikeDoesNotExist() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())

            // act
            val result = likeFacade.addLike(userId = user.id, productId = product.id)

            // assert
            val likes = likeJpaRepository.findAll()
            assertAll(
                { assertThat(result.changed).isTrue() },
                { assertThat(likes).hasSize(1) },
                { assertThat(likes.first().deletedAt).isNull() },
            )
        }

        @DisplayName("이미 활성 좋아요가 있으면 변경하지 않는다.")
        @Test
        fun addLike_noOp_whenLikeIsAlreadyActive() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())
            likeFacade.addLike(userId = user.id, productId = product.id)

            // act
            val result = likeFacade.addLike(userId = user.id, productId = product.id)

            // assert
            assertThat(result.changed).isFalse()
        }

        @DisplayName("취소된 좋아요가 있으면 복구한다.")
        @Test
        fun addLike_restoresLike() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())
            likeFacade.addLike(userId = user.id, productId = product.id)
            likeFacade.cancelLike(userId = user.id, productId = product.id)

            // act
            val result = likeFacade.addLike(userId = user.id, productId = product.id)

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = user.id, productId = product.id)
            assertAll(
                { assertThat(result.changed).isTrue() },
                { assertThat(like?.deletedAt).isNull() },
            )
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                likeFacade.addLike(userId = user.id, productId = 999L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("유저가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = productJpaRepository.save(newProductJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                likeFacade.addLike(userId = 999L, productId = product.id)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("같은 유저의 동일 상품 좋아요 요청이 동시에 들어와도 한 번만 반영된다.")
        @Test
        fun addLike_isIdempotent_whenSameRequestIsConcurrent() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())

            // act
            val results = runConcurrentlyCatching(10) {
                likeFacade.addLike(userId = user.id, productId = product.id)
            }
            val successResults = results.mapNotNull { it.getOrNull() }

            // assert
            val likes = likeJpaRepository.findAll()
            assertAll(
                { assertThat(successResults.count { it.changed }).isEqualTo(1) },
                { assertThat(likes).hasSize(1) },
                { assertThat(likes.first().deletedAt).isNull() },
            )
        }

        @DisplayName("취소된 좋아요 복구 요청이 동시에 들어와도 한 번만 반영된다.")
        @Test
        fun addLike_restoresOnlyOnce_whenCanceledLikeRestoreIsConcurrent() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())
            likeFacade.addLike(userId = user.id, productId = product.id)
            likeFacade.cancelLike(userId = user.id, productId = product.id)

            // act
            val results = runConcurrently(10) {
                likeFacade.addLike(userId = user.id, productId = product.id)
            }

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = user.id, productId = product.id)
            assertAll(
                { assertThat(results.count { it.changed }).isEqualTo(1) },
                { assertThat(like?.deletedAt).isNull() },
            )
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    inner class CancelLike {
        @DisplayName("활성 좋아요가 있으면 좋아요를 취소한다.")
        @Test
        fun cancelLike_whenLikeIsActive() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())
            likeFacade.addLike(userId = user.id, productId = product.id)

            // act
            val result = likeFacade.cancelLike(userId = user.id, productId = product.id)

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = user.id, productId = product.id)
            assertAll(
                { assertThat(result.changed).isTrue() },
                { assertThat(like?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("좋아요가 없으면 변경하지 않는다.")
        @Test
        fun cancelLike_noOp_whenLikeDoesNotExist() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())

            // act
            val result = likeFacade.cancelLike(userId = user.id, productId = product.id)

            // assert
            assertThat(result.changed).isFalse()
        }

        @DisplayName("이미 취소된 좋아요가 있으면 변경하지 않는다.")
        @Test
        fun cancelLike_noOp_whenLikeIsAlreadyCanceled() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())
            likeFacade.addLike(userId = user.id, productId = product.id)
            likeFacade.cancelLike(userId = user.id, productId = product.id)

            // act
            val result = likeFacade.cancelLike(userId = user.id, productId = product.id)

            // assert
            assertThat(result.changed).isFalse()
        }

        @DisplayName("유저가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = productJpaRepository.save(newProductJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                likeFacade.cancelLike(userId = 999L, productId = product.id)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("같은 유저의 동일 상품 좋아요 취소 요청이 동시에 들어와도 한 번만 반영된다.")
        @Test
        fun cancelLike_isIdempotent_whenSameRequestIsConcurrent() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = productJpaRepository.save(newProductJpaEntity())
            likeFacade.addLike(userId = user.id, productId = product.id)

            // act
            val results = runConcurrently(10) {
                likeFacade.cancelLike(userId = user.id, productId = product.id)
            }

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = user.id, productId = product.id)
            assertAll(
                { assertThat(results.count { it.changed }).isEqualTo(1) },
                { assertThat(like?.deletedAt).isNotNull() },
            )
        }
    }

    private fun <T> runConcurrently(times: Int, task: () -> T): List<T> {
        return runConcurrentlyCatching(times, task).map { it.getOrThrow() }
    }

    private fun <T> runConcurrentlyCatching(times: Int, task: () -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)

        return try {
            val futures = (1..times).map {
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task() }
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
    ) = ProductJpaEntity(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
    )
}
