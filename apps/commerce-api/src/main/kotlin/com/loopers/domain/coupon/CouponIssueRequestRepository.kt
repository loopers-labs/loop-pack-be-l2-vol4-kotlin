package com.loopers.domain.coupon

/**
 * 쿠폰 발급 요청 저장소 인터페이스.
 */
interface CouponIssueRequestRepository {
    /** 발급 요청을 저장한다. */
    fun save(request: CouponIssueRequestModel): CouponIssueRequestModel

    /** ID로 발급 요청을 조회한다. */
    fun findById(id: Long): CouponIssueRequestModel?

    /** 해당 사용자가 이미 특정 쿠폰에 대해 발급 요청했는지 확인한다. */
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
}
