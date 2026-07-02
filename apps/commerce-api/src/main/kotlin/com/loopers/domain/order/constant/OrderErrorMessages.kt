package com.loopers.domain.order.constant

object OrderErrorMessages {
    const val ORDER_ID_NEGATIVE = "주문 ID는 음수일 수 없습니다."
    const val PRODUCT_ID_MUST_BE_POSITIVE = "상품 ID는 양수여야 합니다."
    const val SNAPSHOT_PRODUCT_NAME_REQUIRED = "주문 상품명 스냅샷은 필수입니다."
    const val PERSISTED_ORDER_ID_MUST_BE_POSITIVE = "저장된 주문 ID는 양수여야 합니다."
    const val ORDERED_USER_ID_MUST_BE_POSITIVE = "주문자 ID는 양수여야 합니다."
    const val ISSUED_COUPON_ID_MUST_BE_POSITIVE = "발급 쿠폰 ID는 양수여야 합니다."
    const val ORDER_MUST_HAVE_ITEMS = "주문은 하나 이상의 상품을 포함해야 합니다."
    const val DISCOUNT_EXCEEDS_TOTAL = "할인 금액은 주문 총액을 초과할 수 없습니다."
    const val INVALID_STATUS_TRANSITION = "주문 상태 전이는 결제 대기 상태에서만 가능합니다."
}
