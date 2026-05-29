package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class StockQuantity(val value: Int) {
    init {
        if (value <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 수량은 1개 이상이어야 합니다.")
        }
    }
}
