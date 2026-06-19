package com.loopers.application.order

import com.loopers.domain.order.Order

sealed interface OrderConfirmResult {
    /** 이 호출이 PENDING -> PAID 전이의 주인이 되어 결제를 확정함. */
    data class Confirmed(val order: Order) : OrderConfirmResult

    /** 이미 다른 호출이 결제를 확정함(중복 confirm). 멱등 처리 — 환불하면 안 됨. */
    data class AlreadyPaid(val order: Order) : OrderConfirmResult

    /** 결제는 성공했으나 주문이 이미 취소/실패로 종료됨. 결제 취소(환불) 대상. */
    data class AlreadyTerminated(val order: Order) : OrderConfirmResult
}
