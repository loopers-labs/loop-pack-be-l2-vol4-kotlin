package com.loopers.domain.coupon.constant

object CouponErrorMessages {
    const val COUPON_TEMPLATE_NOT_ISSUABLE_DELETED = "삭제된 쿠폰 템플릿은 발급할 수 없습니다."
    const val COUPON_TEMPLATE_NOT_ISSUABLE_EXPIRED = "만료된 쿠폰 템플릿은 발급할 수 없습니다."
    const val COUPON_TEMPLATE_SOLD_OUT = "쿠폰 발급 수량이 소진되었습니다."
    const val COUPON_NOT_USABLE_DELETED = "삭제된 쿠폰은 사용할 수 없습니다."
    const val COUPON_NOT_USABLE_EXPIRED = "만료된 쿠폰은 사용할 수 없습니다."
    const val COUPON_NOT_USABLE_MIN_ORDER_AMOUNT = "최소 주문 금액을 만족하지 않습니다."
    const val COUPON_TEMPLATE_ID_NEGATIVE = "쿠폰 템플릿 ID는 음수일 수 없습니다."
    const val COUPON_TEMPLATE_PERSISTED_ID_NOT_POSITIVE = "저장된 쿠폰 템플릿 ID는 양수여야 합니다."
    const val ISSUED_COUPON_ID_NEGATIVE = "발급 쿠폰 ID는 음수일 수 없습니다."
    const val ISSUED_COUPON_PERSISTED_ID_NOT_POSITIVE = "저장된 발급 쿠폰 ID는 양수여야 합니다."
    const val COUPON_TEMPLATE_ID_NOT_POSITIVE = "쿠폰 템플릿 ID는 양수여야 합니다."
    const val USER_ID_NOT_POSITIVE = "사용자 ID는 양수여야 합니다."
    const val COUPON_NOT_OWNED = "발급 쿠폰을 찾을 수 없습니다."
    const val DUPLICATE_ISSUED_COUPON = "이미 발급받은 쿠폰입니다."
    const val ISSUED_COUPON_NOT_AVAILABLE = "사용 가능한 발급 쿠폰이 아닙니다."
    const val PERCENTAGE_DISCOUNT_RATE_OUT_OF_RANGE = "정률 할인율은 1부터 100 사이여야 합니다."
    const val FIXED_AMOUNT_DISCOUNT_NOT_POSITIVE = "정액 할인 금액은 양수여야 합니다."
    const val COUPON_NAME_REQUIRED = "쿠폰명은 필수입니다."
}
