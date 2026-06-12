package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponInfo
import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCouponStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDateTime

class CouponV1Dto {
    data class SaveCouponRequest(
        @field:NotBlank
        val name: String,

        @field:NotNull
        val type: CouponType,

        @field:Positive
        val value: Long,

        @field:PositiveOrZero
        val minOrderAmount: Long? = null,

        @field:NotNull
        val expiredAt: LocalDateTime,
    ) {
        fun toCreateCommand(): CouponCommand.Create = CouponCommand.Create(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )

        fun toUpdateCommand(): CouponCommand.Update = CouponCommand.Update(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }

    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        companion object {
            fun from(info: CouponInfo.Template) = CouponResponse(
                couponId = info.couponId,
                name = info.name,
                type = info.type,
                value = info.value,
                minOrderAmount = info.minOrderAmount,
                expiredAt = info.expiredAt,
            )
        }
    }

    data class IssuedCouponResponse(
        val issueId: Long,
        val userId: Long,
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
        val status: IssuedCouponStatus,
    ) {
        companion object {
            fun from(info: CouponInfo.Issued) = IssuedCouponResponse(
                issueId = info.issueId,
                userId = info.userId,
                couponId = info.couponId,
                name = info.name,
                type = info.type,
                value = info.value,
                minOrderAmount = info.minOrderAmount,
                expiredAt = info.expiredAt,
                status = info.status,
            )
        }
    }
}
