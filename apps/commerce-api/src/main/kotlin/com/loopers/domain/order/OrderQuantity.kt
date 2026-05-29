package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class OrderQuantity(val value: Int) {
    init {
        if (value <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1개 이상이어야 합니다.")
        }
    }
}
