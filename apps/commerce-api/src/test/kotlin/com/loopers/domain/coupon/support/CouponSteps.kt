package com.loopers.domain.coupon.support

import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.vo.CouponName
import com.loopers.domain.coupon.vo.DiscountPolicy
import com.loopers.domain.coupon.vo.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.vo.PercentageDiscountPolicy
import com.loopers.domain.product.vo.Money
import java.time.LocalDateTime

class CouponSteps {
    companion object {
        const val 기본_쿠폰_템플릿_ID: Long = 10L
        const val 기본_발급_쿠폰_ID: Long = 100L
        const val 기본_사용자_ID: Long = 1L
        const val 기본_쿠폰명: String = "WELCOME_10"
        const val 기본_정액_할인금액: Long = 1_000L
        const val 기본_정률_퍼센트: Int = 10
        const val 기본_최소_주문금액: Long = 10_000L
        val 기준_시각: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
        val 기본_만료시각: LocalDateTime = 기준_시각.plusDays(7)

        fun 정액_할인정책(amount: Long = 기본_정액_할인금액): DiscountPolicy =
            FixedAmountDiscountPolicy.of(Money.of(amount))

        fun 정률_할인정책(percent: Int = 기본_정률_퍼센트): DiscountPolicy =
            PercentageDiscountPolicy.of(percent)

        fun 쿠폰템플릿_도메인_생성(
            id: Long = 기본_쿠폰_템플릿_ID,
            name: String = 기본_쿠폰명,
            discountPolicy: DiscountPolicy = 정률_할인정책(),
            minOrderAmount: Long = 기본_최소_주문금액,
            expiredAt: LocalDateTime = 기본_만료시각,
            deletedAtOrNull: LocalDateTime? = null,
        ): CouponTemplateModel = CouponTemplateModel(
            id = id,
            name = CouponName.of(name),
            discountPolicy = discountPolicy,
            minOrderAmount = Money.of(minOrderAmount),
            expiredAt = expiredAt,
            deletedAtOrNull = deletedAtOrNull,
        )

        fun 발급쿠폰_도메인_생성(
            id: Long = 기본_발급_쿠폰_ID,
            couponTemplateId: Long = 기본_쿠폰_템플릿_ID,
            userId: Long = 기본_사용자_ID,
            issuedAt: LocalDateTime = 기준_시각,
        ): IssuedCouponModel = IssuedCouponModel.issue(
            userId = userId,
            couponTemplateId = couponTemplateId,
            now = issuedAt,
        ).withId(id)
    }
}
