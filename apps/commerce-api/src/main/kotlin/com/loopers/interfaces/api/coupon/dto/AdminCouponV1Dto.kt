package com.loopers.interfaces.api.coupon.dto

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.application.coupon.dto.CouponUpdateCommand
import com.loopers.domain.coupon.enums.CouponIssueDisplayStatus
import com.loopers.domain.coupon.enums.DiscountType
import java.time.ZonedDateTime

class AdminCouponV1Dto {
    data class CreateCouponRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
        val issueLimit: Long? = null,
    ) {
        fun toCommand(): CouponCreateCommand {
            return CouponCreateCommand(
                name = name,
                type = type,
                discountValue = value,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt,
                issueLimit = issueLimit,
            )
        }
    }

    data class UpdateCouponRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
        val issueLimit: Long? = null,
    ) {
        fun toCommand(): CouponUpdateCommand {
            return CouponUpdateCommand(
                name = name,
                type = type,
                discountValue = value,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt,
                issueLimit = issueLimit,
            )
        }
    }

    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val issueLimit: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse {
                return CouponResponse(
                    couponId = info.couponId,
                    name = info.name,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    issueLimit = info.issueLimit,
                    expiredAt = info.expiredAt,
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
