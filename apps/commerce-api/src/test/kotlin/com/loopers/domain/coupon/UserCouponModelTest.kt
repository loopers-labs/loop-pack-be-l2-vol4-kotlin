package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class UserCouponModelTest {
    val expiredAt = ZonedDateTime.of(
        LocalDateTime.of(2020, 1, 1, 0, 0, 0),
        ZoneId.of("UTC"),
    )

    private fun coupon(expiredAt: ZonedDateTime = this.expiredAt): CouponModel =
        CouponModel.of("쿠폰", CouponType.RATE, 10.0, 10000.0, expiredAt, 100)

    @DisplayName("이미 사용한 쿠폰에 use()를 호출하면 예외가 발생한다.")
    @Test
    fun useThrowTest() {
        // given
        val userCoupon = UserCouponModel.of(coupon(), 1L)

        // when
        userCoupon.use()

        // then
        Assertions.assertThatThrownBy { userCoupon.use() }
            .isInstanceOf(CoreException::class.java)
    }

    @DisplayName("statusAt 은 사용 여부와 만료 여부에 따라 USED·EXPIRED·AVAILABLE 을 반환한다")
    @TestFactory
    fun statusAt(): List<DynamicTest> {
        // given
        val now = ZonedDateTime.now()
        val userCouponModel: (ZonedDateTime, Boolean) -> UserCouponModel = { expiredAt, used ->
        UserCouponModel.of(
                CouponModel.of("쿠폰", CouponType.RATE, 10.0, 10000.0, expiredAt, 100), 1L,
            ).apply { if (used) use() }
        }

        // when then
        return listOf(
            Triple("사용됨 → USED", userCouponModel(now.minusDays(1), true), CouponStatus.USED),
            Triple("미사용+만료 → EXPIRED", userCouponModel(now.minusDays(1), false), CouponStatus.EXPIRED),
            Triple("미사용+유효 → AVAILABLE", userCouponModel(now.plusDays(1), false), CouponStatus.AVAILABLE),
        ).map { (name, userCoupon, expected) ->
            DynamicTest.dynamicTest(name) {
                assertThat(userCoupon.statusAt(now)).isEqualTo(expected)
            }
        }
    }
}
