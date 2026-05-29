package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Order(
    val id: Long? = null,
    val userId: Long,
    items: List<OrderItem>,
    status: OrderStatus = OrderStatus.PENDING_PAYMENT,
) {
    val items: List<OrderItem> = items.toList()
    var status: OrderStatus = status
        private set

    val totalPrice: OrderAmount
        get() = items.fold(OrderAmount.ZERO) { acc, item -> acc + item.totalPrice }

    init {
        validate(userId = userId, items = items)
    }

    fun markPaid() {
        guardPendingPayment()
        status = OrderStatus.PAID
    }

    fun markPaymentFailed() {
        guardPendingPayment()
        status = OrderStatus.PAYMENT_FAILED
    }

    private fun guardPendingPayment() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 상태를 변경할 수 있습니다.")
        }
    }

    companion object {
        private fun validate(userId: Long, items: List<OrderItem>) {
            if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 유저 ID 입니다.")
            if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 상품은 최소 1개 이상이어야 합니다.")
        }
    }
}
