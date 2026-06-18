package com.loopers.application.like

import com.loopers.application.like.usecase.LikeProductCommand
import com.loopers.application.like.usecase.LikeProductUsecase
import com.loopers.application.like.usecase.UnlikeProductUsecase
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserService
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class LikeConcurrencyTest @Autowired constructor(
    private val likeProductUsecase: LikeProductUsecase,
    private val unlikeProductUsecase: UnlikeProductUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val likeRepository: LikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("여러 사용자가 동시에 좋아요를 눌러도 좋아요 수가 정확히 반영된다.")
    @Test
    fun likeCountIsAccurate_whenUsersLikeConcurrently() {
        // arrange
        val threadCount = 10
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        val users = (1..threadCount).map { signUp("liker$it") }

        // act
        val errors = runConcurrently(threadCount) { index ->
            likeProductUsecase.execute(
                LikeProductCommand(loginId = users[index].loginId, password = PASSWORD, productId = product.id),
            )
        }

        // assert
        assertAll(
            { assertThat(errors).isEmpty() },
            { assertThat(likeRepository.countByProductId(product.id)).isEqualTo(threadCount.toLong()) },
            { assertThat(productRepository.findActiveById(product.id)!!.likeCount).isEqualTo(threadCount) },
        )
    }

    @DisplayName("여러 사용자가 동시에 좋아요 취소를 해도 좋아요 수가 정확히 반영된다.")
    @Test
    fun likeCountIsAccurate_whenUsersUnlikeConcurrently() {
        // arrange
        val threadCount = 10
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        val users = (1..threadCount).map { signUp("unliker$it") }
        users.forEach {
            likeProductUsecase.execute(LikeProductCommand(loginId = it.loginId, password = PASSWORD, productId = product.id))
        }

        // act
        val errors = runConcurrently(threadCount) { index ->
            unlikeProductUsecase.execute(
                LikeProductCommand(loginId = users[index].loginId, password = PASSWORD, productId = product.id),
            )
        }

        // assert
        assertAll(
            { assertThat(errors).isEmpty() },
            { assertThat(likeRepository.countByProductId(product.id)).isEqualTo(0L) },
            { assertThat(productRepository.findActiveById(product.id)!!.likeCount).isEqualTo(0) },
        )
    }

    private fun signUp(loginId: String) = userService.signUp(
        UserService.SignUpCommand(
            loginId = loginId,
            password = PASSWORD,
            name = "테스터",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "$loginId@loopers.com",
        ),
    )

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
