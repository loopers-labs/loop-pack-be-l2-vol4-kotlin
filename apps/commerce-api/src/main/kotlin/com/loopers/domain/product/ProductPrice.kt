package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class ProductPrice(val amount: Long) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 음수일 수 없습니다.")
        }
    }
}
