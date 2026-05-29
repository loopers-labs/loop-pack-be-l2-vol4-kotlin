package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class OrderItemPrice(val amount: Long) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 상품 가격은 음수일 수 없습니다.")
        }
    }

    operator fun times(quantity: OrderQuantity): OrderAmount = OrderAmount(amount * quantity.value)
}
