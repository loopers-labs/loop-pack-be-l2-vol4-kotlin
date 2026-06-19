package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class DiscountAmount(val amount: Long) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 음수일 수 없습니다.")
        }
    }

    companion object {
        val ZERO: DiscountAmount = DiscountAmount(0L)
    }
}
