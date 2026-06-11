package com.loopers.coupon.interfaces

import com.loopers.account.security.AccountAuthenticationAttributes.ACCOUNT_ID
import com.loopers.coupon.application.CouponService
import com.loopers.coupon.domain.CouponType
import java.time.LocalDateTime
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponController(
    val couponService: CouponService,
) {

    @PostMapping("/api-admin/coupons")
    fun issueCoupon(
        @RequestBody couponIssueRequest: CouponIssueRequest,
        @RequestAttribute(ACCOUNT_ID) requestAccountId: Long,
    ) {
    }
}

data class CouponIssueRequest(
    val couponName: String,
    val expiredAt: LocalDateTime,
    val couponType: CouponType,
    val value: Long,
    val minOrderAmount: Long,
)
