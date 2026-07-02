package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponTemplateTest {
    private val expiredAt = LocalDateTime.parse("2026-12-31T23:59:59")

    private fun template(
        name: String = "테스트 쿠폰",
        type: CouponType = CouponType.FIXED,
        value: Long = 1_000L,
        minOrderAmount: Long = 0L,
        expiredAt: LocalDateTime = this.expiredAt,
        totalCount: Long = 100L,
    ): CouponTemplate = CouponTemplate(
        name = name,
        type = type,
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        totalCount = totalCount,
    )

    @DisplayName("생성 불변식")
    @Nested
    inner class Create {
        @DisplayName("이름이 비어 있으면 실패한다.")
        @Test
        fun rejectsBlankName() {
            assertThrows<IllegalArgumentException> { template(name = " ") }
        }

        @DisplayName("할인 값이 0 이하면 실패한다.")
        @Test
        fun rejectsNonPositiveValue() {
            assertThrows<IllegalArgumentException> { template(value = 0L) }
            assertThrows<IllegalArgumentException> { template(value = -1L) }
        }

        @DisplayName("RATE 의 할인율이 1~100 범위를 벗어나면 실패한다.")
        @Test
        fun rejectsRateOutOfRange() {
            assertThrows<IllegalArgumentException> { template(type = CouponType.RATE, value = 101L) }
        }

        @DisplayName("RATE 의 할인율이 1~100 범위면 성공한다.")
        @Test
        fun acceptsRateInRange() {
            assertThat(template(type = CouponType.RATE, value = 1L).value).isEqualTo(1L)
            assertThat(template(type = CouponType.RATE, value = 100L).value).isEqualTo(100L)
        }

        @DisplayName("minOrderAmount 가 음수면 실패하고, 0 이상이면 성공한다.")
        @Test
        fun rejectsNegativeMinOrderAmount() {
            assertThrows<IllegalArgumentException> { template(minOrderAmount = -1L) }
            assertThat(template(minOrderAmount = 0L).minOrderAmount).isEqualTo(0L)
            assertThat(template(minOrderAmount = 10_000L).minOrderAmount).isEqualTo(10_000L)
        }
    }

    @DisplayName("calculateDiscount(orderAmount)")
    @Nested
    inner class CalculateDiscount {
        @DisplayName("FIXED 는 value 만큼 할인하되 주문 금액을 초과하지 않는다.")
        @Test
        fun fixed() {
            val coupon = template(type = CouponType.FIXED, value = 3_000L)

            assertThat(coupon.calculateDiscount(10_000L)).isEqualTo(3_000L)
            assertThat(coupon.calculateDiscount(2_000L)).isEqualTo(2_000L)
        }

        @DisplayName("RATE 는 orderAmount * value / 100 을 원 단위 내림한다.")
        @Test
        fun rateFloors() {
            val coupon = template(type = CouponType.RATE, value = 10L)

            assertThat(coupon.calculateDiscount(12_345L)).isEqualTo(1_234L)
            assertThat(coupon.calculateDiscount(10_000L)).isEqualTo(1_000L)
        }

        @DisplayName("RATE 100% 라도 할인액은 주문 금액을 초과하지 않는다.")
        @Test
        fun rateNeverExceedsOrderAmount() {
            val coupon = template(type = CouponType.RATE, value = 100L)

            assertThat(coupon.calculateDiscount(5_000L)).isEqualTo(5_000L)
        }
    }

    @DisplayName("update(...) 전체 교체")
    @Nested
    inner class Update {
        @DisplayName("모든 필드를 교체한 새 인스턴스를 반환하고 id 는 유지한다.")
        @Test
        fun replacesAllFields() {
            val origin = CouponTemplate(
                id = 7L,
                name = "원본",
                type = CouponType.FIXED,
                value = 1_000L,
                minOrderAmount = 0L,
                expiredAt = expiredAt,
                totalCount = 50L,
            )
            val newExpiredAt = LocalDateTime.parse("2027-01-01T00:00:00")

            val updated = origin.update(
                name = "변경",
                type = CouponType.RATE,
                value = 30L,
                minOrderAmount = 5_000L,
                expiredAt = newExpiredAt,
            )

            assertThat(updated.id).isEqualTo(7L)
            assertThat(updated.name).isEqualTo("변경")
            assertThat(updated.type).isEqualTo(CouponType.RATE)
            assertThat(updated.value).isEqualTo(30L)
            assertThat(updated.minOrderAmount).isEqualTo(5_000L)
            assertThat(updated.expiredAt).isEqualTo(newExpiredAt)
        }

        @DisplayName("FIXED→RATE 로 바꿀 때 변경된 type 기준으로 불변식을 재검증한다.")
        @Test
        fun revalidatesRateRangeOnTypeChange() {
            val origin = template(type = CouponType.FIXED, value = 1_000L)

            assertThrows<IllegalArgumentException> {
                origin.update(
                    name = "변경",
                    type = CouponType.RATE,
                    value = 101L,
                    minOrderAmount = 0L,
                    expiredAt = expiredAt,
                )
            }
        }

        @DisplayName("이름이 비거나 값이 0 이하로 바뀌면 실패한다.")
        @Test
        fun rejectsInvalidUpdate() {
            val origin = template()

            assertThrows<IllegalArgumentException> {
                origin.update(name = " ", type = CouponType.FIXED, value = 1_000L, minOrderAmount = 0L, expiredAt = expiredAt)
            }
            assertThrows<IllegalArgumentException> {
                origin.update(name = "변경", type = CouponType.FIXED, value = 0L, minOrderAmount = 0L, expiredAt = expiredAt)
            }
        }
    }

    @DisplayName("isApplicableTo(orderAmount, now)")
    @Nested
    inner class IsApplicableTo {
        private val now = LocalDateTime.parse("2026-06-01T12:00:00")

        @DisplayName("만료 시각이 지나면 false 를 반환한다.")
        @Test
        fun falseWhenExpired() {
            val coupon = template(expiredAt = now.minusSeconds(1))

            assertThat(coupon.isApplicableTo(10_000L, now)).isFalse()
        }

        @DisplayName("최소 주문 금액 미달이면 false 를 반환한다.")
        @Test
        fun falseWhenBelowMinOrderAmount() {
            val coupon = template(minOrderAmount = 10_000L)

            assertThat(coupon.isApplicableTo(9_999L, now)).isFalse()
        }

        @DisplayName("최소 주문 금액 조건이 없고(0) 만료 전이면 true 를 반환한다.")
        @Test
        fun trueWhenNoMinAndNotExpired() {
            val coupon = template(minOrderAmount = 0L)

            assertThat(coupon.isApplicableTo(1L, now)).isTrue()
        }

        @DisplayName("최소 주문 금액을 충족하고 만료 전이면 true 를 반환한다.")
        @Test
        fun trueWhenMeetsMin() {
            val coupon = template(minOrderAmount = 10_000L)

            assertThat(coupon.isApplicableTo(10_000L, now)).isTrue()
        }
    }
}
