package com.loopers.application.like

import com.loopers.domain.like.InMemoryLikeRepository
import com.loopers.domain.like.InMemoryProductLikeCountRepository
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.InMemoryProductRepository
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.TechCategory
import com.loopers.domain.user.InMemoryUserRepository
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.domain.value.BirthVO
import com.loopers.domain.value.EmailVO
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate

class LikeFacadeTest {
    private val inMemoryUserRepository = InMemoryUserRepository()
    private val inMemoryProductRepository = InMemoryProductRepository()
    private val inMemoryLikeRepository = InMemoryLikeRepository()
    private val inMemoryProductLikeCountRepository = InMemoryProductLikeCountRepository()
    private val publishedEvents = mutableListOf<Any>()
    private val eventPublisher = ApplicationEventPublisher { publishedEvents.add(it) }

    private val userService = UserService(inMemoryUserRepository)
    private val productService = ProductService(inMemoryProductRepository)
    private val likeService = LikeService(inMemoryLikeRepository, inMemoryProductLikeCountRepository)

    private val likeFacade = LikeFacade(likeService, productService, userService, eventPublisher)

    private fun saveUser(loginId: String = "testId"): UserModel =
        inMemoryUserRepository.save(
            UserModel.of(
                loginId,
                "테스터",
                "test_1234",
                BirthVO(LocalDate.of(1993, 3, 16)),
                EmailVO("test@test.com"),
            ) { it },
        )

    private fun saveProduct(name: String = "테스트 상품"): ProductModel =
        inMemoryProductRepository.save(
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

    @DisplayName("좋아요를 등록할 때")
    @Nested
    internal inner class AddLike {
        @DisplayName("처음 등록하면 true 를 반환한다.")
        @Test
        fun newLike() {
            // given
            val user = saveUser()
            val product = saveProduct()

            // when
            val created = likeFacade.addLike(user.loginId, product.id)

            // then
            assertThat(created).isTrue()
        }

        @DisplayName("이미 좋아요한 상품을 다시 등록하면 false 를 반환한다. (멱등)")
        @Test
        fun duplicateLike() {
            // given
            val user = saveUser()
            val product = saveProduct()
            likeFacade.addLike(user.loginId, product.id)

            // when then
            assertThat(likeFacade.addLike(user.loginId, product.id)).isFalse()
        }
    }

    @DisplayName("좋아요를 취소할 때")
    @Nested
    internal inner class RemoveLike {
        @DisplayName("좋아요한 상품을 취소해도 정상 종료된다.")
        @Test
        fun removeExisting() {
            // given
            val user = saveUser()
            val product = saveProduct()
            likeFacade.addLike(user.loginId, product.id)

            // when then
            assertThatCode { likeFacade.removeLike(user.loginId, product.id) }.doesNotThrowAnyException()
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 예외가 발생하지 않는다. (멱등)")
        @Test
        fun removeNonExisting() {
            // given
            val user = saveUser()
            val product = saveProduct()

            // when then
            assertThatCode { likeFacade.removeLike(user.loginId, product.id) }.doesNotThrowAnyException()
        }
    }

    @DisplayName("좋아요 목록을 조회할 때")
    @Nested
    internal inner class FindLikes {
        @DisplayName("본인이 좋아요한 상품 목록을 반환한다.")
        @Test
        fun success() {
            // given
            val user = saveUser()
            val product1 = saveProduct("상품1")
            val product2 = saveProduct("상품2")
            likeFacade.addLike(user.loginId, product1.id)
            likeFacade.addLike(user.loginId, product2.id)

            // when
            val likes = likeFacade.findLikes(user.loginId, user.id)

            // then
            assertThat(likes).hasSize(2)
            assertThat(likes.map { it.productId }).containsExactlyInAnyOrder(product1.id, product2.id)
        }

        @DisplayName("좋아요가 없으면 빈 목록을 반환한다.")
        @Test
        fun emptyWhenNoLikes() {
            // given
            val user = saveUser()

            // when
            val likes = likeFacade.findLikes(user.loginId, user.id)

            // then
            assertThat(likes).isEmpty()
        }

        @DisplayName("본인이 아닌 다른 유저의 목록을 조회하면 FORBIDDEN CoreException 을 던진다.")
        @Test
        fun forbiddenWhenNotSelf() {
            // given
            val user = saveUser()

            // when then
            assertThatThrownBy { likeFacade.findLikes(user.loginId, 999L) }
                .isInstanceOf(CoreException::class.java)
                .hasMessage("본인만 접근할 수 있습니다.")
        }
    }
}
