package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.enums.DiscountType
import java.time.ZonedDateTime

class Coupon(
    val id: Long = 0L,
    name: String,
    type: DiscountType,
    discountValue: Long,
    minOrderAmount: Long?,
    expiredAt: ZonedDateTime,
    isDeleted: Boolean = false,
) {
    var name: String = name
        private set

    var type: DiscountType = type
        private set

    var discountValue: Long = discountValue
        private set

    var minOrderAmount: Long? = minOrderAmount
        private set

    var expiredAt: ZonedDateTime = expiredAt
        private set

    var isDeleted: Boolean = isDeleted
        private set

    fun update(
        name: String,
        type: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ) {
        this.name = name
        this.type = type
        this.discountValue = discountValue
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    fun delete() {
        isDeleted = true
    }

    fun isValid(): Boolean {
        return !isDeleted && expiredAt.isAfter(ZonedDateTime.now())
    }
}
