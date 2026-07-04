package com.loopers.application.like

import com.loopers.domain.like.ProductLikeCountRepository
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.TechCategory
import com.loopers.domain.user.UserModel
import com.loopers.domain.value.BirthVO
import com.loopers.domain.value.EmailVO
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * 좋아요 → ApplicationEvent(AFTER_COMMIT) → product_like_count 집계 통합 검증.
 * @Transactional 을 붙이면 테스트가 롤백되어 AFTER_COMMIT 이 발화하지 않으므로, 절대 붙이지 않는다.
 * 핸들러는 동기 AFTER_COMMIT 이라 addLike() 리턴 직후 바로 집계를 단언할 수 있다.
 */
@SpringBootTest
class LikeAggregationIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userJpaRepository: UserJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productLikeCountRepository: ProductLikeCountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun saveUser(loginId: String = "testId"): UserModel =
        userJpaRepository.save(
            UserModel.of(loginId, "테스터", "test_1234", BirthVO(LocalDate.of(1993, 3, 16)), EmailVO("test@test.com")) { it },
        )

    private fun saveProduct(name: String = "테스트상품"): ProductModel =
        productJpaRepository.save(
            ProductModel.of(
                brandId = 1L,
                isbn = "isbn-$name",
                name = name,
                authName = "저자",
                techCategory = TechCategory.BACKEND,
                level = Level.BEGINNER,
                price = 1000.0,
                description = "설명",
            ),
        )

    private fun likeCountOf(productId: Long): Int =
        productLikeCountRepository.findByProductIdIn(listOf(productId)).firstOrNull()?.likeCount ?: 0

    @DisplayName("좋아요를 등록하면 AFTER_COMMIT 이벤트로 집계가 +1 된다.")
    @Test
    fun likeIncrementsCount() {
        val user = saveUser()
        val product = saveProduct()

        likeFacade.addLike(user.loginId, product.id)

        assertThat(likeCountOf(product.id)).isEqualTo(1)
    }

    @DisplayName("이미 좋아요한 상품을 다시 등록하면 집계는 그대로다. (멱등)")
    @Test
    fun duplicateLikeKeepsCount() {
        val user = saveUser()
        val product = saveProduct()
        likeFacade.addLike(user.loginId, product.id)

        likeFacade.addLike(user.loginId, product.id)

        assertThat(likeCountOf(product.id)).isEqualTo(1)
    }

    @DisplayName("좋아요를 취소하면 집계가 -1 되고, 이미 취소된 것을 또 취소해도 0 밑으로 가지 않는다.")
    @Test
    fun unlikeDecrementsCountAndIsIdempotent() {
        val user = saveUser()
        val product = saveProduct()
        likeFacade.addLike(user.loginId, product.id)

        likeFacade.removeLike(user.loginId, product.id)
        assertThat(likeCountOf(product.id)).isEqualTo(0)

        // 이미 취소된 것을 다시 취소 → 발행 안 됨(remove 멱등) → 집계 변화 없음, 음수 방지
        likeFacade.removeLike(user.loginId, product.id)
        assertThat(likeCountOf(product.id)).isEqualTo(0)
    }
}
