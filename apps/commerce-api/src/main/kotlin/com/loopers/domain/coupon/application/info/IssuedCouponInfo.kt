package com.loopers.domain.coupon.application.info

import com.loopers.domain.coupon.model.IssuedCouponDisplayStatus
import com.loopers.domain.coupon.model.IssuedCouponModel
import java.time.LocalDateTime

data class IssuedCouponInfo(
    val id: Long,
    val templateId: Long,
    val userId: Long,
    val displayStatus: IssuedCouponDisplayStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            issuedCoupon: IssuedCouponModel,
            template: CouponTemplateInfo,
            now: LocalDateTime,
        ): IssuedCouponInfo = IssuedCouponInfo(
            id = issuedCoupon.id,
            templateId = issuedCoupon.couponTemplateId,
            userId = issuedCoupon.userId,
            displayStatus = issuedCoupon.displayStatus(template.expiredAt, now),
            issuedAt = issuedCoupon.issuedAt,
            usedAt = issuedCoupon.usedAt,
        )
    }
}
