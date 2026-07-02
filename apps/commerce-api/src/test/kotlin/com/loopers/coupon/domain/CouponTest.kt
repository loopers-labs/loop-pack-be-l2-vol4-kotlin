package com.loopers.coupon.domain

import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CouponTest {
    @DisplayName("할인 값이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenValueIsNotPositive() {
        val result = assertThrows<BadRequestException> {
            coupon(type = CouponType.FIXED, value = 0)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.INVALID_DISCOUNT_VALUE)
    }

    @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenRateValueExceeds100() {
        val result = assertThrows<BadRequestException> {
            coupon(type = CouponType.RATE, value = 101)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.RATE_DISCOUNT_OUT_OF_RANGE)
    }

    @DisplayName("주문 금액이 최소 주문 금액보다 적으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenOrderAmountBelowMinimum() {
        val coupon = coupon(minOrderAmount = Money(10000))

        val result = assertThrows<BadRequestException> {
            coupon.validateUsable(orderAmount = 9999, now = NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.MIN_ORDER_NOT_MET)
    }

    @DisplayName("만료된 쿠폰을 사용하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenCouponExpired() {
        val coupon = coupon(expiredAt = NOW.minusDays(1))

        val result = assertThrows<BadRequestException> {
            coupon.validateUsable(orderAmount = 10000, now = NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.EXPIRED)
    }

    @DisplayName("최소 주문 금액을 충족하고 만료되지 않았으면 검증을 통과한다.")
    @Test
    fun passesValidation_whenAmountMeetsMinimumAndNotExpired() {
        val coupon = coupon(minOrderAmount = Money(10000), expiredAt = NOW.plusDays(1))

        assertThatCode {
            coupon.validateUsable(orderAmount = 10000, now = NOW)
        }.doesNotThrowAnyException()
    }

    @DisplayName("발급 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenTotalQuantityIsNotPositive() {
        listOf(0L, -1L).forEach { totalQuantity ->
            val result = assertThrows<BadRequestException> {
                coupon(totalQuantity = totalQuantity)
            }

            assertThat(result.errorCode).isEqualTo(CouponErrorCode.INVALID_TOTAL_QUANTITY)
        }
    }

    @DisplayName("발급 수량이 양수이거나 미설정(null)이면 쿠폰 생성에 성공한다.")
    @Test
    fun createsCoupon_whenTotalQuantityIsPositiveOrNull() {
        assertThatCode {
            coupon(totalQuantity = 1)
            coupon(totalQuantity = null)
        }.doesNotThrowAnyException()
    }

    @DisplayName("발급 수량이 설정되지 않은 쿠폰을 발급하면 BAD_REQUEST(NOT_ISSUABLE) 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenIssuingCouponWithoutTotalQuantity() {
        val coupon = coupon(totalQuantity = null)

        val result = assertThrows<BadRequestException> {
            coupon.issue(NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.NOT_ISSUABLE)
    }

    @DisplayName("만료된 쿠폰을 발급하면 수량이 남아 있어도 BAD_REQUEST(EXPIRED) 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenIssuingExpiredCoupon() {
        val coupon = coupon(totalQuantity = 10, expiredAt = NOW.minusDays(1))

        val result = assertThrows<BadRequestException> {
            coupon.issue(NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.EXPIRED)
    }

    @DisplayName("발급하면 발급 수량이 1 증가하고, 한도에 도달하면 매진 상태가 된다.")
    @Test
    fun increasesIssuedQuantity_andReachesSoldOutAtLimit() {
        val coupon = coupon(totalQuantity = 2)

        coupon.issue(NOW)
        coupon.issue(NOW)

        assertThat(coupon.issuedQuantity).isEqualTo(2)
        assertThat(coupon.isSoldOut()).isTrue()
    }

    @DisplayName("매진된 쿠폰을 발급하면 CONFLICT(SOLD_OUT) 예외가 발생하고 발급 수량은 변하지 않는다.")
    @Test
    fun throwsConflict_whenIssuingSoldOutCoupon() {
        val coupon = coupon(totalQuantity = 1)
        coupon.issue(NOW)

        val result = assertThrows<ConflictException> {
            coupon.issue(NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.SOLD_OUT)
        assertThat(coupon.issuedQuantity).isEqualTo(1)
    }

    private fun coupon(
        type: CouponType = CouponType.FIXED,
        value: Long = 1000,
        minOrderAmount: Money = Money(0),
        expiredAt: LocalDateTime = NOW.plusDays(1),
        totalQuantity: Long? = null,
    ): Coupon = Coupon(
        type = type,
        name = "테스트쿠폰",
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        createdBy = 1L,
        totalQuantity = totalQuantity,
    )

    private companion object {
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
