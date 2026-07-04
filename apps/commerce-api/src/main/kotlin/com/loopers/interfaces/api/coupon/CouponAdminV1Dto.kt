package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.application.coupon.UserCouponInfo
import com.loopers.domain.coupon.CouponType as DomainCouponType

import java.time.ZonedDateTime

class CouponAdminV1Dto {
    data class CreateCouponRequest(
        val name: String,
        val type: CouponType,
        val value: Double,
        val minOrderAmount: Double,
        val expiredAt: ZonedDateTime,
        val quantity: Int,
    )

    data class UpdateCouponRequest(
        val name: String,
        val expiredAt: ZonedDateTime,
    )

    data class CouponResponse private constructor(
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: Double,
        val minOrderAmount: Double,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(adminCouponInfo: AdminCouponInfo) =
                CouponResponse(
                    couponId = adminCouponInfo.id,
                    name = adminCouponInfo.name,
                    type = CouponAdminV1Dto.CouponType.from(adminCouponInfo.type),
                    value = adminCouponInfo.value,
                    minOrderAmount = adminCouponInfo.minOrderAmount,
                    expiredAt = adminCouponInfo.expiredAt,
                    )
        }
    }

    data class CouponHistoryResponse(
        val userId: Long,
        val issuedAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(userCouponInfo: UserCouponInfo) =
                CouponHistoryResponse(
                    userId = userCouponInfo.userId,
                    issuedAt = userCouponInfo.issuedAt,
                    usedAt = userCouponInfo.usedAt,
                )
        }
    }

    enum class CouponType {
        FIXED,
        RATE;

        fun toDomain(): DomainCouponType = when (this) {
            FIXED -> DomainCouponType.FIXED
            RATE  -> DomainCouponType.RATE
        }

        companion object {
            fun from(domain: DomainCouponType): CouponType = when (domain) {
                DomainCouponType.FIXED -> FIXED
                DomainCouponType.RATE -> RATE
            }
        }
    }
}
