package com.loopers.order.domain

import com.loopers.support.error.ErrorCode

enum class OrderErrorCode(
    override val message: String,
) : ErrorCode {
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다."),
    EMPTY_ORDER_ITEMS("주문 항목이 비어 있습니다."),
    INVALID_ORDER_QUANTITY("주문 수량이 올바르지 않습니다."),
    PRICE_CHANGED("주문 금액이 변경되었습니다. 변경된 금액을 확인해주세요."),
    INVALID_STATUS_TRANSITION("현재 상태에서 변경할 수 없는 주문 상태입니다."),
    ;

    override val code: String
        get() = "ORDER:$name"
}
