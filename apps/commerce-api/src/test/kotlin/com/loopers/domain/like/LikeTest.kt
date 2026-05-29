package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class LikeTest {
    @DisplayName("좋아요 생성 시, ")
    @Nested
    inner class CreateLike {
        @DisplayName("유저 ID와 상품 ID가 유효하면 정상적으로 생성된다.")
        @Test
        fun createLike_whenAllFieldsAreValid() {
            // arrange
            val userId = 1L
            val productId = 10L

            // act
            val like = Like(userId = userId, productId = productId)

            // assert
            assertAll(
                { assertThat(like.userId).isEqualTo(userId) },
                { assertThat(like.productId).isEqualTo(productId) },
                { assertThat(like.active).isTrue() },
            )
        }

        @DisplayName("유저 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUserIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                Like(userId = 0L, productId = 10L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                Like(userId = 1L, productId = 0L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("좋아요 취소 가능 여부 확인 시, ")
    @Nested
    inner class CanCancelLike {
        @DisplayName("활성 좋아요는 true를 반환한다.")
        @Test
        fun canCancel_returnsTrue_whenLikeIsActive() {
            // arrange
            val like = Like(userId = 1L, productId = 10L)

            // act
            val result = like.canCancel()

            // assert
            assertAll(
                { assertThat(result).isTrue() },
                { assertThat(like.active).isTrue() },
            )
        }

        @DisplayName("취소된 좋아요는 false를 반환한다.")
        @Test
        fun canCancel_returnsFalse_whenLikeIsAlreadyCanceled() {
            // arrange
            val like = Like(userId = 1L, productId = 10L, active = false)

            // act
            val result = like.canCancel()

            // assert
            assertAll(
                { assertThat(result).isFalse() },
                { assertThat(like.active).isFalse() },
            )
        }
    }

    @DisplayName("좋아요 활성화 가능 여부 확인 시, ")
    @Nested
    inner class CanActivateLike {
        @DisplayName("취소된 좋아요는 true를 반환한다.")
        @Test
        fun canActivate_returnsTrue_whenLikeIsCanceled() {
            // arrange
            val like = Like(userId = 1L, productId = 10L, active = false)

            // act
            val result = like.canActivate()

            // assert
            assertAll(
                { assertThat(result).isTrue() },
                { assertThat(like.active).isFalse() },
            )
        }

        @DisplayName("이미 활성화된 좋아요는 false를 반환한다.")
        @Test
        fun canActivate_returnsFalse_whenLikeIsAlreadyActive() {
            // arrange
            val like = Like(userId = 1L, productId = 10L)

            // act
            val result = like.canActivate()

            // assert
            assertAll(
                { assertThat(result).isFalse() },
                { assertThat(like.active).isTrue() },
            )
        }
    }
}
