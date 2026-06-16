package com.loopers.coupon.domain

import com.loopers.support.error.ConflictException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class UserCouponTest {
    private fun userCoupon(): UserCoupon = UserCoupon(
        userId = 1L,
        couponId = 10L,
        grantedType = UserCouponGrantedType.ADMIN,
        grantedBy = 100L,
    )

    @DisplayName("쿠폰을 사용하면, 상태가 USED로 바뀌고 사용 시각이 기록된다.")
    @Test
    fun use_marksUsed_andRecordsUsedAt() {
        val userCoupon = userCoupon()

        userCoupon.use(NOW)

        assertAll(
            { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
            { assertThat(userCoupon.usedAt).isEqualTo(NOW) },
        )
    }

    @DisplayName("이미 사용한 쿠폰을 다시 사용하면, CONFLICT(ALREADY_USED) 예외가 발생한다.")
    @Test
    fun use_throwsConflict_whenAlreadyUsed() {
        val userCoupon = userCoupon()
        userCoupon.use(NOW)

        val result = assertThrows<ConflictException> {
            userCoupon.use(NOW.plusMinutes(1))
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.ALREADY_USED)
    }

    @DisplayName("사용 취소하면, 상태가 AVAILABLE로 돌아오고 사용 시각이 지워진다.")
    @Test
    fun cancelUse_restoresAvailable_andClearsUsedAt() {
        val userCoupon = userCoupon()
        userCoupon.use(NOW)

        userCoupon.cancelUse()

        assertAll(
            { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE) },
            { assertThat(userCoupon.usedAt).isNull() },
        )
    }

    @DisplayName("사용하지 않은 쿠폰을 사용 취소해도, 아무 일도 일어나지 않는다. (보상 멱등성)")
    @Test
    fun cancelUse_isIdempotent_whenNotUsed() {
        val userCoupon = userCoupon()

        userCoupon.cancelUse()

        assertAll(
            { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE) },
            { assertThat(userCoupon.usedAt).isNull() },
        )
    }

    @DisplayName("사용 취소한 쿠폰은 다시 사용할 수 있다.")
    @Test
    fun use_succeedsAgain_afterCancelUse() {
        val userCoupon = userCoupon()
        userCoupon.use(NOW)
        userCoupon.cancelUse()

        userCoupon.use(NOW.plusMinutes(5))

        assertAll(
            { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
            { assertThat(userCoupon.usedAt).isEqualTo(NOW.plusMinutes(5)) },
        )
    }

    private companion object {
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 12, 14, 0)
    }
}
