package com.loopers.application.like

import com.loopers.infrastructure.like.LikeJpaEntity
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LikeApplicationServiceIntegrationTest @Autowired constructor(
    private val likeApplicationService: LikeApplicationService,
    private val likeJpaRepository: LikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    inner class AddLike {
        @DisplayName("기존 좋아요가 없으면 좋아요를 생성하고 true를 반환한다.")
        @Test
        fun addLike_createsLike_whenLikeDoesNotExist() {
            // act
            val changed = likeApplicationService.activate(userId = 1L, productId = 10L)

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = 1L, productId = 10L)
            assertAll(
                { assertThat(changed).isTrue() },
                { assertThat(like).isNotNull() },
                { assertThat(like?.deletedAt).isNull() },
            )
        }

        @DisplayName("이미 활성 좋아요가 있으면 상태를 변경하지 않고 false를 반환한다.")
        @Test
        fun addLike_noOp_whenLikeIsAlreadyActive() {
            // arrange
            likeJpaRepository.save(LikeJpaEntity(userId = 1L, productId = 10L))

            // act
            val changed = likeApplicationService.activate(userId = 1L, productId = 10L)

            // assert
            val likes = likeJpaRepository.findAll()
            assertAll(
                { assertThat(changed).isFalse() },
                { assertThat(likes).hasSize(1) },
                { assertThat(likes.first().deletedAt).isNull() },
            )
        }

        @DisplayName("취소된 좋아요가 있으면 복구하고 true를 반환한다.")
        @Test
        fun addLike_restoresLike_whenLikeIsCanceled() {
            // arrange
            val entity = likeJpaRepository.save(LikeJpaEntity(userId = 1L, productId = 10L))
            entity.delete()
            likeJpaRepository.save(entity)

            // act
            val changed = likeApplicationService.activate(userId = 1L, productId = 10L)

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = 1L, productId = 10L)
            assertAll(
                { assertThat(changed).isTrue() },
                { assertThat(like?.deletedAt).isNull() },
            )
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    inner class CancelLike {
        @DisplayName("활성 좋아요가 있으면 취소하고 true를 반환한다.")
        @Test
        fun cancelLike_cancelsLike_whenLikeIsActive() {
            // arrange
            likeJpaRepository.save(LikeJpaEntity(userId = 1L, productId = 10L))

            // act
            val changed = likeApplicationService.cancel(userId = 1L, productId = 10L)

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = 1L, productId = 10L)
            assertAll(
                { assertThat(changed).isTrue() },
                { assertThat(like?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("좋아요가 없으면 상태를 변경하지 않고 false를 반환한다.")
        @Test
        fun cancelLike_noOp_whenLikeDoesNotExist() {
            // act
            val changed = likeApplicationService.cancel(userId = 1L, productId = 10L)

            // assert
            assertAll(
                { assertThat(changed).isFalse() },
                { assertThat(likeJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("이미 취소된 좋아요가 있으면 상태를 변경하지 않고 false를 반환한다.")
        @Test
        fun cancelLike_noOp_whenLikeIsAlreadyCanceled() {
            // arrange
            val entity = likeJpaRepository.save(LikeJpaEntity(userId = 1L, productId = 10L))
            entity.delete()
            likeJpaRepository.save(entity)

            // act
            val changed = likeApplicationService.cancel(userId = 1L, productId = 10L)

            // assert
            val like = likeJpaRepository.findByUserIdAndProductId(userId = 1L, productId = 10L)
            assertAll(
                { assertThat(changed).isFalse() },
                { assertThat(like?.deletedAt).isNotNull() },
            )
        }
    }
}
