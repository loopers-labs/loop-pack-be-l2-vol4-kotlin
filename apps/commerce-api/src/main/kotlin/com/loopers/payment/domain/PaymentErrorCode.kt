package com.loopers.payment.domain

import com.loopers.support.error.ErrorCode

enum class PaymentErrorCode(
    override val message: String,
) : ErrorCode {
    PAYMENT_NOT_FOUND("결제를 찾을 수 없습니다."),
    INVALID_STATUS_TRANSITION("현재 상태에서 변경할 수 없는 결제 상태입니다."),
    ORDER_NOT_PAYABLE("결제 가능한 상태의 주문이 아닙니다."),
    ;

    override val code: String
        get() = "PAYMENT:$name"
}
