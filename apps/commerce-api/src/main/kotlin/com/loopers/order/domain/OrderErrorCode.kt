package com.loopers.order.domain

import com.loopers.support.error.ErrorCode

enum class OrderErrorCode(
    override val message: String,
) : ErrorCode {
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다."),
    EMPTY_ORDER_ITEMS("주문 항목이 비어 있습니다."),
    INVALID_ORDER_QUANTITY("주문 수량이 올바르지 않습니다."),
    ;

    override val code: String
        get() = "ORDER:$name"
}
