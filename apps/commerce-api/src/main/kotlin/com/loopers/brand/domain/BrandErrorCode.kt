package com.loopers.brand.domain

import com.loopers.support.error.ErrorCode

enum class BrandErrorCode(
    override val message: String,
) : ErrorCode {
    BRAND_NOT_FOUND("브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND_NAME("이미 존재하는 브랜드 이름입니다."),
    INVALID_BRAND_NAME("브랜드 이름이 올바르지 않습니다."),
    INVALID_BRAND_STATUS_TRANSITION("허용되지 않은 브랜드 상태 전이입니다."),
    ;

    override val code: String
        get() = "BRAND:$name"
}
