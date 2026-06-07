package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDateTime

class MyCouponResultTest {
    private val now = LocalDateTime.parse("2026-06-07T12:00:00")
    private val issuedAt = LocalDateTime.parse("2026-06-01T10:00:00")

    private fun template(expiredAt: LocalDateTime): CouponTemplate = CouponTemplate(
        id = 1L,
        name = "1만원 할인",
        type = CouponType.FIXED,
        value = 10_000L,
        minOrderAmount = 5_000L,
        expiredAt = expiredAt,
    )

    @DisplayName("AVAILABLE 이고 템플릿이 만료 전이면, 표시 상태는 AVAILABLE 이다.")
    @Test
    fun availableWhenNotExpired() {
        val userCoupon = UserCoupon(
            id = 100L,
            couponTemplateId = 1L,
            userId = 9L,
            status = CouponStatus.AVAILABLE,
            issuedAt = issuedAt,
        )

        val result = MyCouponResult.of(userCoupon, template(now.plusDays(10)), now)

        assertThat(result.status).isEqualTo(CouponStatus.AVAILABLE)
    }

    @DisplayName("AVAILABLE 이어도 템플릿이 만료됐으면, 표시 상태는 EXPIRED 이다.")
    @Test
    fun expiredWhenTemplateExpired() {
        val userCoupon = UserCoupon(
            id = 100L,
            couponTemplateId = 1L,
            userId = 9L,
            status = CouponStatus.AVAILABLE,
            issuedAt = issuedAt,
        )

        val result = MyCouponResult.of(userCoupon, template(now.minusDays(1)), now)

        assertThat(result.status).isEqualTo(CouponStatus.EXPIRED)
    }

    @DisplayName("USED 쿠폰은 템플릿이 만료됐어도 USED 를 유지한다.")
    @Test
    fun usedStaysUsedEvenIfExpired() {
        val userCoupon = UserCoupon(
            id = 100L,
            couponTemplateId = 1L,
            userId = 9L,
            status = CouponStatus.USED,
            issuedAt = issuedAt,
            usedAt = now.minusDays(2),
        )

        val result = MyCouponResult.of(userCoupon, template(now.minusDays(1)), now)

        assertThat(result.status).isEqualTo(CouponStatus.USED)
    }

    @DisplayName("템플릿 정보(name/type/value/expiredAt)와 발급 정보가 함께 채워진다.")
    @Test
    fun composesTemplateAndIssueInfo() {
        val expiredAt = now.plusDays(10)
        val userCoupon = UserCoupon(
            id = 100L,
            couponTemplateId = 1L,
            userId = 9L,
            status = CouponStatus.AVAILABLE,
            issuedAt = issuedAt,
        )

        val result = MyCouponResult.of(userCoupon, template(expiredAt), now)

        assertAll(
            { assertThat(result.id).isEqualTo(100L) },
            { assertThat(result.couponTemplateId).isEqualTo(1L) },
            { assertThat(result.name).isEqualTo("1만원 할인") },
            { assertThat(result.type).isEqualTo(CouponType.FIXED) },
            { assertThat(result.value).isEqualTo(10_000L) },
            { assertThat(result.minOrderAmount).isEqualTo(5_000L) },
            { assertThat(result.issuedAt).isEqualTo(issuedAt) },
            { assertThat(result.expiredAt).isEqualTo(expiredAt) },
        )
    }
}
