package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Stream

class ApplyCouponTest {
    val couponModel = CouponModel.of(
        name = "테스트 쿠폰",
        type = CouponType.RATE,
        value = 30.0,
        minOrderAmount = MIN_ORDER_AMOUNT,
        expiredAt = EXPIRED_AT,
        quantity = 100,
    )

    fun userCoupon(used: Boolean = false) =
        UserCouponModel.of(couponModel, OWNER_ID)
            .apply { if (used) use() }

    @DisplayName("정상적인 값이 들어오면 할인액을 반환하고 UserCoupon은 USED로 변한다.")
    @Test
    fun applyCouponSuccessTest() {
        // given
        val userId = 1L
        val orderAmount = 20000.0
        val userCouponModel = UserCouponModel.of(couponModel, userId)
        
        // when
        Assertions.assertThat(userCouponModel.status).isEqualTo(CouponStatus.AVAILABLE)
        val result = applyCoupon(userCouponModel, userId, orderAmount, NOW)
        
        // then
        Assertions.assertThat(userCouponModel.status).isEqualTo(CouponStatus.USED)
        Assertions.assertThat(result).isEqualTo(6000.0)
    }

    @DisplayName("applyCoupon 실패 테스트")
    @MethodSource("invalidCouponParams")
    @ParameterizedTest(name = "{0}")
    fun applyCouponFailureTest(name: String, used: Boolean, userId: Long, orderAmount: Double, now: ZonedDateTime, expected: ErrorType) {
        // given
        val userCoupon = userCoupon(used)

        // when then
        assertThatThrownBy { applyCoupon(
            userCoupon = userCoupon,
            userId = userId,
            orderAmount = orderAmount,
            now = now
        ) }
            .isInstanceOf(CoreException::class.java)
            .hasFieldOrPropertyWithValue("errorType", expected)

        if (!used) {
            Assertions.assertThat(userCoupon.status).isEqualTo(CouponStatus.AVAILABLE)
        }
    }

    companion object {
        private val NOW = ZonedDateTime.of(LocalDateTime.of(2026, 1, 1, 0, 0, 0), ZoneId.of("UTC"))
        private val EXPIRED_AT = NOW.plusDays(1)
        private const val OWNER_ID = 1L
        private const val MIN_ORDER_AMOUNT = 15000.0

        @JvmStatic
        fun invalidCouponParams(): Stream<Arguments> = Stream.of(
            Arguments.of("타인의 쿠폰이면 FORBIDDEN", false, 2L, 18000.0, NOW, ErrorType.FORBIDDEN),
            Arguments.of("만료된 쿠폰이면 BAD_REQUEST", false, OWNER_ID, 18000.0, NOW.plusDays(10), ErrorType.BAD_REQUEST),
            Arguments.of("최소 주문 금액 미달이면 BAD_REQUEST", false, OWNER_ID, 11999.0, NOW, ErrorType.BAD_REQUEST),
            Arguments.of("이미 사용한 쿠폰이면 CONFLICT", true, OWNER_ID, 18000.0, NOW, ErrorType.CONFLICT),
        )
    }
}
