package com.loopers.product.domain

import com.loopers.support.error.ErrorCode

enum class ProductErrorCode(
    override val message: String,
) : ErrorCode {
    PRODUCT_NOT_FOUND("상품을 찾을 수 없습니다."),
    INVALID_PRODUCT_NAME("상품 이름이 올바르지 않습니다."),
    INVALID_PRODUCT_STATUS_TRANSITION("허용되지 않은 상품 상태 전이입니다."),
    INVALID_PRODUCT_CURSOR("정렬 기준에 맞지 않는 커서입니다."),
    INVALID_PAGE_SIZE("페이지 크기가 올바르지 않습니다."),
    PRODUCT_PRICE_NOT_MATCHED("요청된 가격과 상품의 가격이 일치하지 않습니다."),
    ;

    override val code: String
        get() = "PRODUCT:$name"
}
