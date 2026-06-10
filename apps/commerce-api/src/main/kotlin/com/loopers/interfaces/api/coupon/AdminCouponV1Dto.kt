package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.application.coupon.dto.CouponUpdateCommand
import com.loopers.domain.coupon.CouponIssueDisplayStatus
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

class AdminCouponV1Dto {
    data class CreateCouponRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(): CouponCreateCommand {
            return CouponCreateCommand(
                name,
                type,
                value,
                minOrderAmount,
                expiredAt,
            )
        }
    }

    data class UpdateCouponRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(): CouponUpdateCommand {
            return CouponUpdateCommand(
                name,
                type,
                value,
                minOrderAmount,
                expiredAt,
            )
        }
    }

    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse {
                return CouponResponse(
                    info.couponId,
                    info.name,
                    info.type,
                    info.value,
                    info.minOrderAmount,
                    info.expiredAt,
                )
            }
        }
    }

    data class CouponIssueResponse(
        val issueId: Long,
        val couponId: Long,
        val memberId: Long,
        val status: CouponIssueDisplayStatus,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponIssueInfo): CouponIssueResponse {
                return CouponIssueResponse(
                    issueId = info.issueId,
                    couponId = info.couponId,
                    memberId = info.memberId,
                    status = info.status,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    usedAt = info.usedAt,
                )
            }
        }
    }
}
