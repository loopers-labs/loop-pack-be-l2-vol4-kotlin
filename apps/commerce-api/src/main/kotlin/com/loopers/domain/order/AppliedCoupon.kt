package com.loopers.domain.order

/**
 * 주문에 적용된 쿠폰의 스냅샷(주문 시점 값 동결).
 * coupon 도메인을 참조하지 않기 위해 [couponType] 은 enum 이 아닌 String 으로 박제한다.
 */
data class AppliedCoupon(
    val issuedCouponId: Long,
    val couponName: String,
    val couponType: String,
    val couponValue: Long,
    val discountAmount: Long,
) {
    init {
        require(issuedCouponId > 0) { "발급 쿠폰 ID는 0보다 커야 합니다." }
        require(couponName.isNotBlank()) { "쿠폰명 스냅샷은 비어 있을 수 없습니다." }
        require(couponType.isNotBlank()) { "쿠폰 타입 스냅샷은 비어 있을 수 없습니다." }
        require(couponValue > 0) { "쿠폰 할인 값 스냅샷은 0보다 커야 합니다." }
        require(discountAmount >= 0) { "할인 금액은 0 이상이어야 합니다." }
    }
}
