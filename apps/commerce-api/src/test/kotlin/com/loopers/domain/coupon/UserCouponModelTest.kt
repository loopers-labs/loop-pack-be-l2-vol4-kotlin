package com.loopers.domain.coupon

import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.ZonedDateTime

class UserCouponModelTest {
    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    inner class Use {
        @DisplayName("사용 가능한 쿠폰이면 USED로 전이되고 사용 시각이 기록된다.")
        @Test
        fun marksAsUsed_whenAvailable() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)

            userCoupon.use(coupon = coupon, now = now)

            assertAll(
                { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
                { assertThat(userCoupon.usedAt).isEqualTo(now) },
            )
        }

        @DisplayName("이미 사용한 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadyUsed() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)
            userCoupon.use(coupon = coupon, now = now)

            val exception = assertThrows<CoreException> { userCoupon.use(coupon = coupon, now = now) }

            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenExpired() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.minusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)

            val exception = assertThrows<CoreException> { userCoupon.use(coupon = coupon, now = now) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("다른 템플릿의 쿠폰으로 사용하려 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCouponMismatches() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id + 1)

            val exception = assertThrows<CoreException> { userCoupon.use(coupon = coupon, now = now) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("현재 상태를 조회할 때, 미사용이지만 템플릿이 만료됐으면 EXPIRED를 반환한다.")
    @Test
    fun currentStatusIsExpired_whenAvailableButTemplateExpired() {
        val now = ZonedDateTime.now()
        val coupon = coupon(expiredAt = now.minusDays(1))
        val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)

        assertThat(userCoupon.currentStatus(coupon = coupon, now = now)).isEqualTo(UserCouponStatus.EXPIRED)
    }

    private fun coupon(expiredAt: ZonedDateTime) = CouponModel(
        name = "테스트 쿠폰",
        type = CouponType.FIXED,
        discountValue = BigDecimal("1000"),
        minOrderAmount = null,
        expiredAt = expiredAt,
    ).withId(100L)
}
