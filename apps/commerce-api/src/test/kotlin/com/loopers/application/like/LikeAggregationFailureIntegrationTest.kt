package com.loopers.application.like

import com.loopers.domain.like.ProductLikeCountModel
import com.loopers.domain.like.ProductLikeCountRepository
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.TechCategory
import com.loopers.domain.user.UserModel
import com.loopers.domain.value.BirthVO
import com.loopers.domain.value.EmailVO
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 과제 핵심 요건 검증: "집계 실패와 무관하게 좋아요는 성공한다."
 * @Primary 로 upsert 가 항상 예외를 던지는 가짜 ProductLikeCountRepository 를 주입한다.
 * (이 가짜가 클래스 전체에 적용되므로 정상 집계 검증과 분리된 별도 클래스로 둔다.)
 */
@SpringBootTest
@Import(LikeAggregationFailureIntegrationTest.FailingConfig::class)
class LikeAggregationFailureIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userJpaRepository: UserJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @TestConfiguration
    class FailingConfig {
        @Bean
        @Primary
        fun failingProductLikeCountRepository(): ProductLikeCountRepository =
            object : ProductLikeCountRepository {
                override fun findByProductIdIn(productIds: List<Long>): List<ProductLikeCountModel> = emptyList()
                override fun upsertLikeCount(productId: Long, delta: Int, now: ZonedDateTime): Int =
                    throw RuntimeException("집계 실패 시뮬레이션")
            }
    }

    @DisplayName("집계 핸들러가 예외를 던져도 좋아요 자체는 성공한다.")
    @Test
    fun likeSucceedsEvenIfAggregationFails() {
        val user = userJpaRepository.save(
            UserModel.of("testId", "테스터", "test_1234", BirthVO(LocalDate.of(1993, 3, 16)), EmailVO("test@test.com")) { it },
        )
        val product = productJpaRepository.save(
            ProductModel.of(
                brandId = 1L,
                isbn = "isbn-fail",
                name = "테스트상품",
                authName = "저자",
                techCategory = TechCategory.BACKEND,
                level = Level.BEGINNER,
                price = 1000.0,
                description = "설명",
            ),
        )

        // 집계 upsert 가 예외를 던지지만, runCatching 이 삼키므로 addLike 는 정상 종료해야 한다
        assertThatCode { likeFacade.addLike(user.loginId, product.id) }.doesNotThrowAnyException()

        // 좋아요 row 는 커밋되어 살아있어야 한다
        val like = likeJpaRepository.findByUserIdAndProductId(user.id, product.id)
        assertThat(like).isNotNull
        assertThat(like!!.available()).isTrue()
    }
}
