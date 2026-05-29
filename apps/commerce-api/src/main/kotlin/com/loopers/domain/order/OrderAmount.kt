package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class OrderAmount(val amount: Long) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 금액은 음수일 수 없습니다.")
        }
    }

    operator fun plus(other: OrderAmount): OrderAmount = OrderAmount(amount + other.amount)

    companion object {
        val ZERO: OrderAmount = OrderAmount(0)
    }
}
