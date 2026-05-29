package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class Stock(val value: Int) {
    init {
        if (value < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 재고는 음수일 수 없습니다.")
        }
    }

    fun validateDeductible(quantity: StockQuantity) {
        if (value < quantity.value) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 재고가 부족합니다.")
        }
    }

    fun isEmpty(): Boolean = value == 0
}
