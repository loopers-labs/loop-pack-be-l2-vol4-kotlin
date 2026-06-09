package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Coupon(
    val id: Long? = null,
    name: String,
    val policy: DiscountPolicy,
) {
    var name: String = name
        private set

    init {
        validateName(name)
    }

    fun discountOf(targetAmount: DiscountAmount): DiscountAmount = policy.discountOf(targetAmount)

    fun rename(newName: String) {
        validateName(newName)
        this.name = newName
    }

    companion object {
        private fun validateName(name: String) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.")
        }
    }
}
