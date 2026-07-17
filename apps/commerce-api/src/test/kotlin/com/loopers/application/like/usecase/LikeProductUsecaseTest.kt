package com.loopers.application.like.usecase

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.like.LikeModel
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.LocalDate

class LikeProductUsecaseTest {
    @DisplayName("상품 좋아요를 등록할 때,")
    @Nested
    inner class Like {
        @DisplayName("처음 등록하면 좋아요를 저장하고 이벤트를 발행한다.")
        @Test
        fun savesLikeAndPublishesEvent() {
            // arrange
            val fixture = Fixture()

            // act
            fixture.likeProductUsecase.execute(fixture.command())

            // assert
            assertThat(fixture.likeRepository.countByProductId(10L)).isEqualTo(1L)
            assertThat(fixture.eventPublisher.events).containsExactly(LikeCreatedEvent(productId = 10L))
        }

        @DisplayName("이미 등록된 좋아요면 멱등하게 아무 일도 하지 않는다.")
        @Test
        fun doesNothing_whenLikeAlreadyExists() {
            // arrange
            val fixture = Fixture()
            fixture.likeProductUsecase.execute(fixture.command())

            // act
            fixture.likeProductUsecase.execute(fixture.command())

            // assert
            assertThat(fixture.likeRepository.countByProductId(10L)).isEqualTo(1L)
            assertThat(fixture.eventPublisher.events).containsExactly(LikeCreatedEvent(productId = 10L))
        }
    }

    @DisplayName("상품 좋아요를 취소할 때,")
    @Nested
    inner class Unlike {
        @DisplayName("등록된 좋아요가 있으면 삭제하고 이벤트를 발행한다.")
        @Test
        fun deletesLikeAndPublishesEvent() {
            // arrange
            val fixture = Fixture()
            fixture.likeProductUsecase.execute(fixture.command())

            // act
            fixture.unlikeProductUsecase.execute(fixture.command())

            // assert
            assertThat(fixture.likeRepository.countByProductId(10L)).isEqualTo(0L)
            assertThat(fixture.eventPublisher.events).containsExactly(
                LikeCreatedEvent(productId = 10L),
                LikeDeletedEvent(productId = 10L),
            )
        }

        @DisplayName("등록된 좋아요가 없어도 멱등하게 성공한다.")
        @Test
        fun doesNothing_whenLikeDoesNotExist() {
            // arrange
            val fixture = Fixture()

            // act
            fixture.unlikeProductUsecase.execute(fixture.command())

            // assert
            assertThat(fixture.likeRepository.countByProductId(10L)).isEqualTo(0L)
            assertThat(fixture.eventPublisher.events).isEmpty()
        }
    }

    private class Fixture {
        private val userRepository = InMemoryUserRepository()
        private val productRepository = InMemoryProductRepository()
        val likeRepository = InMemoryLikeRepository()
        val eventPublisher = RecordingEventPublisher()
        private val userService = UserService(userRepository)
        val likeProductUsecase = LikeProductUsecase(
            userService = userService,
            productRepository = productRepository,
            likeRepository = likeRepository,
            eventPublisher = eventPublisher,
        )
        val unlikeProductUsecase = UnlikeProductUsecase(
            userService = userService,
            productRepository = productRepository,
            likeRepository = likeRepository,
            eventPublisher = eventPublisher,
        )

        init {
            userRepository.save(
                UserModel(
                    loginId = "loopers01",
                    rawPassword = "Pass1234!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "loopers@example.com",
                ).withId(1L),
            )
            productRepository.save(
                ProductModel(
                    brandId = 1L,
                    name = "Air Max",
                    description = "Shoes",
                    price = BigDecimal("120000.00"),
                ).withId(10L),
            )
        }

        fun command(): LikeProductCommand {
            return LikeProductCommand(
                loginId = "loopers01",
                password = "Pass1234!",
                productId = 10L,
            )
        }
    }

    private class RecordingEventPublisher : ApplicationEventPublisher {
        val events = mutableListOf<Any>()

        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    private class InMemoryLikeRepository : LikeRepository {
        private val likes = mutableMapOf<Pair<Long, Long>, LikeModel>()

        override fun save(like: LikeModel): LikeModel {
            likes[like.userId to like.productId] = like
            return like
        }

        override fun findByUserIdAndProductId(userId: Long, productId: Long): LikeModel? {
            return likes[userId to productId]
        }

        override fun delete(like: LikeModel) {
            likes.remove(like.userId to like.productId)
        }

        override fun countByProductId(productId: Long): Long {
            return likes.values.count { it.productId == productId }.toLong()
        }
    }

    private class InMemoryUserRepository : UserRepository {
        private val users = mutableMapOf<Long, UserModel>()

        override fun save(user: UserModel): UserModel {
            users[user.id] = user
            return user
        }

        override fun findById(id: Long): UserModel? {
            return users[id]
        }

        override fun findByLoginId(loginId: String): UserModel? {
            return users.values.firstOrNull { it.loginId == loginId }
        }

        override fun existsByLoginId(loginId: String): Boolean {
            return findByLoginId(loginId) != null
        }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val products = mutableMapOf<Long, ProductModel>()

        override fun save(product: ProductModel): ProductModel {
            products[product.id] = product
            return product
        }

        override fun findActiveById(id: Long): ProductModel? {
            return products[id]?.takeUnless { it.isDeleted() }
        }

        override fun findActiveAllByIds(ids: List<Long>): List<ProductModel> {
            return ids.mapNotNull { findActiveById(it) }
        }

        override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> {
            return PageImpl(products.values.toList(), pageable, products.size.toLong())
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }

        override fun incrementLikeCount(productId: Long) {
            findActiveById(productId)?.incrementLikeCount()
        }

        override fun decrementLikeCount(productId: Long) {
            findActiveById(productId)?.decrementLikeCount()
        }
    }
}
