package com.loopers.domain.coupon

class CouponIssueResult(
    val id: Long? = null,
    val requestId: String,
    val userId: Long,
    val couponId: Long,
    status: CouponIssueStatus = CouponIssueStatus.PENDING,
    reason: String? = null,
) {
    var status: CouponIssueStatus = status
        private set

    var reason: String? = reason
        private set

    fun isPending(): Boolean = status == CouponIssueStatus.PENDING

    fun succeed() {
        status = CouponIssueStatus.SUCCESS
        reason = null
    }

    fun fail(reason: String) {
        status = CouponIssueStatus.FAILED
        this.reason = reason
    }

    companion object {
        fun pending(requestId: String, userId: Long, couponId: Long): CouponIssueResult =
            CouponIssueResult(
                requestId = requestId,
                userId = userId,
                couponId = couponId,
            )
    }
}

enum class CouponIssueStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
