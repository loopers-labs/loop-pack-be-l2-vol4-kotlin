package com.loopers.domain.coupon

data class CouponIssueRequest(
    val id: Long = 0L,
    val userId: Long,
    val couponTemplateId: Long,
    val status: CouponIssueStatus,
    val idempotencyKey: String,
    val failureReason: CouponIssueFailureReason? = null,
) {
    init {
        require(userId > 0) { "회원 ID는 0보다 커야 합니다." }
        require(couponTemplateId > 0) { "쿠폰 템플릿 ID는 0보다 커야 합니다." }
        require(idempotencyKey.isNotBlank()) { "멱등성 키는 비어 있을 수 없습니다." }
    }

    fun complete(): CouponIssueRequest = copy(status = CouponIssueStatus.COMPLETED, failureReason = null)

    fun fail(reason: CouponIssueFailureReason): CouponIssueRequest =
        copy(status = CouponIssueStatus.FAILED, failureReason = reason)

    companion object {
        fun pending(userId: Long, couponTemplateId: Long, idempotencyKey: String): CouponIssueRequest =
            CouponIssueRequest(
                userId = userId,
                couponTemplateId = couponTemplateId,
                status = CouponIssueStatus.PENDING,
                idempotencyKey = idempotencyKey,
            )
    }
}
