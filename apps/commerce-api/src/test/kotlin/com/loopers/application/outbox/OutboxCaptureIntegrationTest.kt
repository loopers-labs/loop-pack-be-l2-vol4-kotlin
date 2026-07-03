package com.loopers.application.outbox

import com.loopers.application.like.usecase.LikeProductCommand
import com.loopers.application.like.usecase.LikeProductUsecase
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class OutboxCaptureIntegrationTest @Autowired constructor(
    private val likeProductUsecase: LikeProductUsecase,
    private val outboxRepository: OutboxEventRepository,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("좋아요 등록이 커밋되면 같은 트랜잭션으로 outbox 행이 생성된다.")
    @Test
    fun likeCreatesOutboxRow() {
        // arrange: 활성 유저/상품 저장
        userService.signUp(
            UserService.SignUpCommand(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = "테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$LOGIN_ID@loopers.com",
            ),
        )
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        val productId = product.id

        // act
        likeProductUsecase.execute(LikeProductCommand(LOGIN_ID, PASSWORD, productId))

        // assert
        val pending = outboxRepository.findTopPending(10)
        assertThat(pending).hasSize(1)
        assertThat(pending.first().topic).isEqualTo(KafkaTopics.CATALOG_EVENTS)
        assertThat(pending.first().partitionKey).isEqualTo(productId.toString())
    }

    companion object {
        private const val LOGIN_ID = "tester"
        private const val PASSWORD = "Password1!"
    }
}
