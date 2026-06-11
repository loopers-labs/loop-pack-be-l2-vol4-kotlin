package com.loopers.coupon.interfaces

import com.loopers.account.infrastructure.security.AccountAuthenticationAttributes.ACCOUNT_ID
import com.loopers.coupon.application.CouponCreateCommand
import com.loopers.coupon.application.CouponService
import com.loopers.coupon.domain.CouponType
import java.time.LocalDateTime
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponController(
    val couponService: CouponService,
) {

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
}

data class CouponGrantRequest(
    val userId: Long,
)

data class CouponCreateRequest(
    val couponName: String,
    val expiredAt: LocalDateTime,
    val couponType: CouponType,
    val value: Long,
    val minOrderAmount: Long,
) {
    fun toCommand(requestAccountId: Long): CouponCreateCommand = CouponCreateCommand(
        couponName = this.couponName,
        expiredAt = this.expiredAt,
        couponType = this.couponType,
        value = this.value,
        minOrderAmount = this.minOrderAmount,
        requestAccountId = requestAccountId,
    )
}
