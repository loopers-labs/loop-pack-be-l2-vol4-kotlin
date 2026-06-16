package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

class CouponIssue(
    val id: Long = 0L,
    val memberId: Long,
    val couponId: Long,
    status: CouponIssueStatus,
    val type: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
    usedAt: ZonedDateTime?,
) {
    var status: CouponIssueStatus = status
        private set

    var usedAt: ZonedDateTime? = usedAt
        private set

    init {
        if (memberId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Member id must be positive.")
        }
        if (couponId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon id must be positive.")
        }
        if (minOrderAmount != null && minOrderAmount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "MinimumOrderAmount cannot be less than zero.")
        }
    }

    fun displayStatusAt(now: ZonedDateTime): CouponIssueDisplayStatus {
        if (status == CouponIssueStatus.USED) {
            return CouponIssueDisplayStatus.USED
        }

        if (!expiredAt.isAfter(now)) {
            return CouponIssueDisplayStatus.EXPIRED
        }

        return CouponIssueDisplayStatus.AVAILABLE
    }

    fun use(memberId: Long, orderAmount: Long, now: ZonedDateTime = ZonedDateTime.now()): Long {
        if (this.memberId != memberId) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon issue does not belong to member.")
        }
        if (status != CouponIssueStatus.AVAILABLE) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon issue is not available.")
        }
        if (!expiredAt.isAfter(now)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon issue is expired.")
        }
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order amount is less than coupon minimum amount.")
        }

        status = CouponIssueStatus.USED
        usedAt = now

        return when (type) {
            DiscountType.FIXED -> discountValue.coerceAtMost(orderAmount)
            DiscountType.RATE -> orderAmount * discountValue / 100
        }
    }

    companion object {
        fun issue(
            memberId: Long,
            coupon: Coupon,
        ): CouponIssue {
            return CouponIssue(
                memberId = memberId,
                couponId = coupon.id,
                status = CouponIssueStatus.AVAILABLE,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                usedAt = null,
            )
        }
    }
}
