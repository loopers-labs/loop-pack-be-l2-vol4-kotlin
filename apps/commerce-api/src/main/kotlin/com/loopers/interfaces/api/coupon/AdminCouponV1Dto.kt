package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.application.coupon.CouponIssueInfo
import com.loopers.application.coupon.PageResult
import com.loopers.application.coupon.UpsertCouponCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AdminCouponV1Dto {
    data class CouponUpsertRequest(
        val name: String,
        val type: CouponType,
        val value: BigDecimal,
        val minOrderAmount: BigDecimal?,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): UpsertCouponCommand {
            return UpsertCouponCommand(
                name = name,
                type = type,
                discountValue = value,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt.atZone(ZoneId.systemDefault()),
            )
        }
    }

    data class CouponResponse(
        val id: Long,
        val name: String,
        val type: CouponType,
        val value: BigDecimal,
        val minOrderAmount: BigDecimal?,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: AdminCouponInfo): CouponResponse {
                return CouponResponse(
                    id = info.id,
                    name = info.name,
                    type = info.type,
                    value = info.discountValue,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                )
            }
        }
    }

    data class CouponPageResponse(
        val items: List<CouponResponse>,
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(result: PageResult<AdminCouponInfo>): CouponPageResponse {
                return CouponPageResponse(
                    items = result.items.map { CouponResponse.from(it) },
                    page = result.page,
                    size = result.size,
                    totalCount = result.totalCount,
                )
            }
        }
    }

    data class CouponIssueResponse(
        val id: Long,
        val userId: Long,
        val status: UserCouponStatus,
        val issuedAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponIssueInfo): CouponIssueResponse {
                return CouponIssueResponse(
                    id = info.id,
                    userId = info.userId,
                    status = info.status,
                    issuedAt = info.issuedAt,
                    usedAt = info.usedAt,
                )
            }
        }
    }

    data class CouponIssuePageResponse(
        val items: List<CouponIssueResponse>,
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(result: PageResult<CouponIssueInfo>): CouponIssuePageResponse {
                return CouponIssuePageResponse(
                    items = result.items.map { CouponIssueResponse.from(it) },
                    page = result.page,
                    size = result.size,
                    totalCount = result.totalCount,
                )
            }
        }
    }
}
