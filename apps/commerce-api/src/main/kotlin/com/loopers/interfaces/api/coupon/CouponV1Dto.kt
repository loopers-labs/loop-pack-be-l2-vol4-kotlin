package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.MyCouponInfo
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponType as DomainCouponType
import com.loopers.domain.coupon.CouponStatus as DomainCouponStatus
import java.time.ZonedDateTime

class CouponV1Dto {
    data class CouponResponse(
        val userCouponId: Long,
        val name: String,
        val type: CouponV1Type,
        val value: Double,
        val minOrderAmount: Double,
        val expiredAt: ZonedDateTime,
        val status: CouponV1Status,
    ) {
        companion object {
            fun from(info: MyCouponInfo): CouponResponse =
                CouponResponse(
                    userCouponId = info.userCouponId,
                    name = info.name,
                    type = CouponV1Type.from(info.type),
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    status = CouponV1Status.from(info.status)
                )
        }
    }

    enum class CouponV1Type {
        FIXED,
        RATE;

        companion object {
            fun from(type: DomainCouponType) = when (type) {
                DomainCouponType.FIXED -> FIXED
                DomainCouponType.RATE  -> RATE
            }
        }
    }

    enum class CouponV1Status {
        AVAILABLE,
        USED,
        EXPIRED;

        companion object {
            fun from(status: DomainCouponStatus) = when (status) {
                DomainCouponStatus.AVAILABLE -> AVAILABLE
                DomainCouponStatus.USED -> USED
                DomainCouponStatus.EXPIRED -> EXPIRED
            }
        }
    }

    data class CouponIssueRequestResponse(
        val requestId: String,
        val status: CouponIssueStatus = CouponIssueStatus.PENDING
    )

    data class CouponIssueRequestInfo(
        val requestId: String,
        val status: CouponIssueStatus,
    )
}
