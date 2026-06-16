package com.loopers.shared.domain

import com.loopers.support.error.ErrorCode

enum class MoneyErrorCode(
    override val message: String,
) : ErrorCode {
    INVALID_MONEY("금액은 0 이상이어야 합니다."),
    ;

    override val code: String
        get() = "MONEY:$name"
}
