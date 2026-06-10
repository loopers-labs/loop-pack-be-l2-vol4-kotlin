package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class CouponTest {
    @DisplayName("Coupon 생성")
    @Nested
    inner class Create {
        @DisplayName("정률 쿠폰 생성 조건이 유효하면 정상적으로 생성된다")
        @Test
        fun createsRateCoupon_whenRequiredFieldsAreValid() {
            val expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00")

            val coupon = Coupon(
                name = "신규가입 10% 할인",
                type = DiscountType.RATE,
                discountValue = 10L,
                minOrderAmount = 10_000L,
                expiredAt = expiredAt,
            )

            assertAll(
                { assertThat(coupon.id).isZero() },
                { assertThat(coupon.name).isEqualTo("신규가입 10% 할인") },
                { assertThat(coupon.type).isEqualTo(DiscountType.RATE) },
                { assertThat(coupon.discountValue).isEqualTo(10L) },
                { assertThat(coupon.minOrderAmount).isEqualTo(10_000L) },
                { assertThat(coupon.expiredAt).isEqualTo(expiredAt) },
                { assertThat(coupon.isDeleted).isFalse() },
            )
        }
    }

    @DisplayName("Coupon 재구성")
    @Nested
    inner class Restore {
        @DisplayName("만료된 쿠폰도 도메인 객체로 재구성할 수 있다")
        @Test
        fun restoresExpiredCoupon() {
            val expiredAt = ZonedDateTime.parse("2000-01-01T00:00:00+09:00")

            val coupon = createCoupon(expiredAt = expiredAt)

            assertThat(coupon.expiredAt).isEqualTo(expiredAt)
        }
    }

    @DisplayName("쿠폰 유효성")
    @Nested
    inner class Valid {
        @DisplayName("쿠폰이 삭제되지 않았고 만료 전이면 유효하다")
        @Test
        fun returnsTrue_whenCouponIsNotDeletedAndNotExpired() {
            val coupon = createCoupon(expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"))

            val result = coupon.isValid()

            assertThat(result).isTrue()
        }

        @DisplayName("쿠폰이 삭제되었으면 유효하지 않다")
        @Test
        fun returnsFalse_whenCouponIsDeleted() {
            val coupon = createCoupon(expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"))
            coupon.delete()

            val result = coupon.isValid()

            assertThat(result).isFalse()
        }

        @DisplayName("쿠폰이 만료되었으면 유효하지 않다")
        @Test
        fun returnsFalse_whenCouponIsExpired() {
            val coupon = createCoupon(expiredAt = ZonedDateTime.parse("2000-01-01T00:00:00+09:00"))

            val result = coupon.isValid()

            assertThat(result).isFalse()
        }
    }

    private fun createCoupon(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
    ): Coupon {
        return Coupon(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }
}
