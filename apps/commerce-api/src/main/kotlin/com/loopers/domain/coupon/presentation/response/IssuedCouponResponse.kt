package com.loopers.domain.coupon.presentation.response

import com.loopers.domain.coupon.application.info.IssuedCouponInfo
import java.time.LocalDateTime

data class IssuedCouponResponse(
    val id: Long,
    val templateId: Long,
    val userId: Long,
    val displayStatus: String,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun from(info: IssuedCouponInfo): IssuedCouponResponse = IssuedCouponResponse(
            id = info.id,
            templateId = info.templateId,
            userId = info.userId,
            displayStatus = info.displayStatus.name,
            issuedAt = info.issuedAt,
            usedAt = info.usedAt,
        )
    }
}
