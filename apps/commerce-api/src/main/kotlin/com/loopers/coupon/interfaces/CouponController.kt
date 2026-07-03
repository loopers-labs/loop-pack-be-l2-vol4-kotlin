package com.loopers.coupon.interfaces

import com.loopers.account.infrastructure.security.AccountAuthenticationAttributes.ACCOUNT_ID
import com.loopers.coupon.application.CouponCreateCommand
import com.loopers.coupon.application.CouponIssueCommand
import com.loopers.coupon.application.CouponIssueInfo
import com.loopers.coupon.application.CouponService
import com.loopers.coupon.domain.CouponType
import java.time.LocalDateTime
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponController(val couponService: CouponService) {

    @PostMapping("/api-admin/v1/coupons")
    fun createCoupon(
        @RequestBody couponCreateRequest: CouponCreateRequest,
        @RequestAttribute(ACCOUNT_ID) requestAccountId: Long,
    ) = couponService.create(couponCreateRequest.toCommand(requestAccountId))

    @PostMapping("/api-admin/v1/coupons/{couponId}/grant")
    fun grantCoupon(
        @PathVariable couponId: Long,
        @RequestBody couponGrantRequest: CouponGrantRequest,
        @RequestAttribute(ACCOUNT_ID) requestAccountId: Long,
    ) = couponService.grant(couponId, couponGrantRequest.userId, requestAccountId)

    @PostMapping("/api/v1/coupons/issue")
    fun issueCoupon(
        @RequestBody couponIssueRequest: CouponIssueRequest,
        @RequestAttribute(ACCOUNT_ID) userId: Long,
    ): CouponIssueResponse = CouponIssueResponse.from(couponService.issue(couponIssueRequest.toCommand(userId)))
}

data class CouponIssueRequest(val couponId: Long) {
    fun toCommand(userId: Long): CouponIssueCommand = CouponIssueCommand(
        couponId = this.couponId,
        userId = userId,
    )
}

data class CouponIssueResponse(
    val userCouponId: Long,
    val couponId: Long,
    val couponName: String,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun from(couponIssueInfo: CouponIssueInfo): CouponIssueResponse = CouponIssueResponse(
            userCouponId = couponIssueInfo.userCouponId,
            couponId = couponIssueInfo.couponId,
            couponName = couponIssueInfo.couponName,
            expiredAt = couponIssueInfo.expiredAt,
        )
    }
}

data class CouponGrantRequest(val userId: Long)

data class CouponCreateRequest(
    val couponName: String,
    val expiredAt: LocalDateTime,
    val couponType: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    // 선착순 발급 한도. 생략(null) 시 관리자 지급 전용 쿠폰
    val totalQuantity: Long? = null,
) {
    fun toCommand(requestAccountId: Long): CouponCreateCommand = CouponCreateCommand(
        couponName = this.couponName,
        expiredAt = this.expiredAt,
        couponType = this.couponType,
        value = this.value,
        minOrderAmount = this.minOrderAmount,
        requestAccountId = requestAccountId,
        totalQuantity = this.totalQuantity,
    )
}
