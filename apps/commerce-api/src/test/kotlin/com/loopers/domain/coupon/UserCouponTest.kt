package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
}
