package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.enums.DiscountType
import java.time.ZonedDateTime

class Coupon(
    val id: Long = 0L,
    val name: String,
    val type: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
    val isDeleted: Boolean,
    val issueLimit: Long?,
    issuedCount: Long,
) {
    var issuedCount: Long = issuedCount
        private set

    fun isValid(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        return !isDeleted && expiredAt.isAfter(now)
    }

    fun hasRemainingIssueQuantity(): Boolean {
        return issueLimit?.let { issuedCount < it } ?: true
    }

    fun increaseIssuedCount() {
        check(hasRemainingIssueQuantity()) { "Coupon issue limit exceeded." }
        issuedCount += 1
    }
}
