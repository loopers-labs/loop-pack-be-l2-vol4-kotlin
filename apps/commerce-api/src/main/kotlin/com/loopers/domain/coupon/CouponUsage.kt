package com.loopers.domain.coupon

/**
 * 쿠폰을 주문에 사용한 결과(coupon 도메인 표현).
 * 사용 완료(USED)로 전이된 발급 쿠폰, 적용된 템플릿, 계산된 할인액을 함께 담는다.
 * order 도메인의 스냅샷(AppliedCoupon) 조립은 application 계층의 책임이다.
 */
data class CouponUsage(
    val usedCoupon: UserCoupon,
    val template: CouponTemplate,
    val discount: Long,
)
