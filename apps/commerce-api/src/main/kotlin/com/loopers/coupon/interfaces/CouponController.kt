package com.loopers.coupon.interfaces

import com.loopers.account.infrastructure.security.AccountAuthenticationAttributes.ACCOUNT_ID
import com.loopers.coupon.application.CouponCreateCommand
import com.loopers.coupon.application.CouponIssueAcceptedInfo
import com.loopers.coupon.application.CouponIssueCommand
import com.loopers.coupon.application.CouponIssueResultInfo
import com.loopers.coupon.application.CouponService
import com.loopers.coupon.domain.CouponIssueResultStatus
import com.loopers.coupon.domain.CouponType
import java.time.LocalDateTime
import org.springframework.web.bind.annotation.GetMapping
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
    ): CouponIssueAcceptedResponse =
        CouponIssueAcceptedResponse.from(couponService.requestIssue(couponIssueRequest.toCommand(userId)))

    @GetMapping("/api/v1/coupons/issue/{requestId}")
    fun getIssueResult(
        @PathVariable requestId: String,
    ): CouponIssueResultResponse = CouponIssueResultResponse.from(couponService.getIssueResult(requestId))
}

data class CouponIssueRequest(val couponId: Long) {
    fun toCommand(userId: Long): CouponIssueCommand = CouponIssueCommand(
        couponId = this.couponId,
        userId = userId,
    )
}

data class CouponIssueAcceptedResponse(
    val requestId: String,
    val status: CouponIssueResultStatus,
) {
    companion object {
        fun from(couponIssueAcceptedInfo: CouponIssueAcceptedInfo): CouponIssueAcceptedResponse =
            CouponIssueAcceptedResponse(
                requestId = couponIssueAcceptedInfo.requestId,
                status = couponIssueAcceptedInfo.status,
            )
    }
}

data class CouponIssueResultResponse(
    val requestId: String,
    val status: CouponIssueResultStatus,
    val userCouponId: Long?,
    val rejectReason: String?,
    val requestedAt: LocalDateTime?,
    val decidedAt: LocalDateTime?,
) {
    companion object {
        fun from(couponIssueResultInfo: CouponIssueResultInfo): CouponIssueResultResponse = CouponIssueResultResponse(
            requestId = couponIssueResultInfo.requestId,
            status = couponIssueResultInfo.status,
            userCouponId = couponIssueResultInfo.userCouponId,
            rejectReason = couponIssueResultInfo.rejectReason,
            requestedAt = couponIssueResultInfo.requestedAt,
            decidedAt = couponIssueResultInfo.decidedAt,
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
