package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Stock(
    val id: Long? = null,
    val productId: Long,
    quantity: Int,
) {
    var quantity: Int = quantity
        private set

    init {
        if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 상품 ID 입니다.")
        if (quantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고 수량은 음수일 수 없습니다.")
    }

    fun validateDeductible(amount: Int) {
        if (amount <= 0) throw CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.")
        if (quantity < amount) throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.")
    }

    fun isSoldOut(): Boolean = quantity == 0
}
