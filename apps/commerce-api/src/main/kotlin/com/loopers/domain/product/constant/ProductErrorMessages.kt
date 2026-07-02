package com.loopers.domain.product.constant

object ProductErrorMessages {
    const val PRICE_MUST_NOT_BE_NEGATIVE = "가격은 음수일 수 없습니다."
    const val PRODUCT_NAME_MUST_NOT_BE_BLANK = "상품명은 공백일 수 없습니다."
    const val QUANTITY_MUST_BE_POSITIVE = "수량은 1개 이상이어야 합니다."
    const val STOCK_MUST_NOT_BE_NEGATIVE = "재고는 음수일 수 없습니다."
    const val UNSUPPORTED_PRODUCT_SORT = "지원하지 않는 상품 정렬조건입니다."
    const val LIKES_SORT_HANDLED_BY_QUERYDSL = "좋아요순 정렬은 QueryDSL 경로에서 처리합니다."
}
