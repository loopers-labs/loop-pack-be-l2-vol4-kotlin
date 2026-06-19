package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class OrderAmounts(
    val totalAmount: OrderAmount,
    val discountAmount: OrderAmount,
    val paymentAmount: OrderAmount,
) {
    init {
        if (discountAmount.amount > totalAmount.amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.")
        }
        if (paymentAmount.amount != totalAmount.amount - discountAmount.amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "최종 결제 금액이 올바르지 않습니다.")
        }
    }

    companion object {
        fun of(
            totalAmount: OrderAmount,
            discountAmount: OrderAmount = OrderAmount.ZERO,
        ): OrderAmounts {
            return OrderAmounts(
                totalAmount = totalAmount,
                discountAmount = discountAmount,
                paymentAmount = OrderAmount(totalAmount.amount - discountAmount.amount),
            )
        }
    }
}
