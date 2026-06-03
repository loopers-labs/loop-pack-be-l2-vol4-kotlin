package com.loopers.domain.like

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LikeServiceTest {
    private lateinit var likeRepositoryPort: LikeRepositoryPort
    private lateinit var likeService: LikeService

    @BeforeEach
    fun setUp() {
        likeRepositoryPort = mockk(relaxed = true)
        likeService = LikeService(likeRepositoryPort)
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    inner class Register {
        @DisplayName("동일 회원-상품 좋아요가 존재하지 않으면, 새로 저장한다.")
        @Test
        fun savesNewLike_whenNotExists() {
            // arrange
            every { likeRepositoryPort.findByUserIdAndProductId(1L, 10L) } returns null

            // act
            likeService.register(1L, 10L)

            // assert
            verify(exactly = 1) {
                likeRepositoryPort.save(Like(userId = 1L, productId = 10L))
            }
        }

        @DisplayName("동일 회원-상품 좋아요가 이미 존재하면, 저장 없이 멱등하게 동작한다.")
        @Test
        fun doesNothing_whenAlreadyExists() {
            // arrange
            val existing = Like(id = 99L, userId = 1L, productId = 10L)
            every { likeRepositoryPort.findByUserIdAndProductId(1L, 10L) } returns existing

            // act
            likeService.register(1L, 10L)

            // assert
            verify(exactly = 0) { likeRepositoryPort.save(any()) }
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    inner class Cancel {
        @DisplayName("동일 회원-상품 좋아요가 존재하면, 삭제한다.")
        @Test
        fun deletes_whenExists() {
            // arrange
            val existing = Like(id = 99L, userId = 1L, productId = 10L)
            every { likeRepositoryPort.findByUserIdAndProductId(1L, 10L) } returns existing

            // act
            likeService.cancel(1L, 10L)

            // assert
            verify(exactly = 1) { likeRepositoryPort.delete(existing) }
        }

        @DisplayName("동일 회원-상품 좋아요가 존재하지 않으면, 삭제 없이 멱등하게 동작한다.")
        @Test
        fun doesNothing_whenNotExists() {
            // arrange
            every { likeRepositoryPort.findByUserIdAndProductId(1L, 10L) } returns null

            // act
            likeService.cancel(1L, 10L)

            // assert
            verify(exactly = 0) { likeRepositoryPort.delete(any()) }
        }
    }

    @DisplayName("deleteAllByProductId는 포트의 deleteAllByProductId를 호출한다.")
    @Test
    fun delegatesDeleteAllByProductId() {
        likeService.deleteAllByProductId(10L)

        verify(exactly = 1) { likeRepositoryPort.deleteAllByProductId(10L) }
    }

    @DisplayName("initializeForProduct는 포트의 initializeForProduct를 호출한다.")
    @Test
    fun delegatesInitializeForProduct() {
        likeService.initializeForProduct(10L)

        verify(exactly = 1) { likeRepositoryPort.initializeForProduct(10L) }
    }
}
