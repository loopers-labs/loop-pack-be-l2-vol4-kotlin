package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime

class UserCouponTest {

    @DisplayName("발급된 쿠폰 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 userId, couponId 이면 정상적으로 생성되며, 초기 상태는 미사용이다.")
        @Test
        fun create_whenAllFieldsAreValid() {
            // act
            val userCoupon = UserCoupon(userId = 1L, couponId = 10L)

            // assert
            assertThat(userCoupon.userId).isEqualTo(1L)
            assertThat(userCoupon.couponId).isEqualTo(10L)
            assertThat(userCoupon.isUsed()).isFalse()
            assertThat(userCoupon.usedAt).isNull()
        }

        @DisplayName("userId가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenUserIdIsNotPositive(userId: Long) {
            // act & assert
            val result = assertThrows<CoreException> { UserCoupon(userId = userId, couponId = 10L) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("couponId가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenCouponIdIsNotPositive(couponId: Long) {
            // act & assert
            val result = assertThrows<CoreException> { UserCoupon(userId = 1L, couponId = couponId) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("소유권 검증 시, ")
    @Nested
    inner class ValidateOwnedBy {
        @DisplayName("본인 소유의 쿠폰이면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenOwnedByUser() {
            // arrange
            val userCoupon = UserCoupon(userId = 1L, couponId = 10L)

            // act & assert
            assertDoesNotThrow { userCoupon.validateOwnedBy(1L) }
        }

        @DisplayName("다른 유저의 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNotOwnedByUser() {
            // arrange
            val userCoupon = UserCoupon(userId = 1L, couponId = 10L)

            // act & assert
            val result = assertThrows<CoreException> { userCoupon.validateOwnedBy(2L) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("사용 가능 검증 시, ")
    @Nested
    inner class ValidateUsable {
        @DisplayName("미사용 상태이면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenNotUsed() {
            // arrange
            val userCoupon = UserCoupon(userId = 1L, couponId = 10L)

            // act & assert
            assertDoesNotThrow { userCoupon.validateUsable() }
        }

        @DisplayName("이미 사용된 상태이면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadyUsed() {
            // arrange
            val userCoupon = UserCoupon(userId = 1L, couponId = 10L, usedAt = LocalDateTime.now())

            // act & assert
            val result = assertThrows<CoreException> { userCoupon.validateUsable() }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
