package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponResult
import com.loopers.application.coupon.CreateCouponCommand
import com.loopers.application.coupon.UpdateCouponCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDateTime

class CouponAdminV1Dto {
    data class CreateCouponRequest(
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long? = null,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): CreateCouponCommand = CreateCouponCommand(
            name = name,
            type = parseType(type),
            value = value,
            minOrderAmount = minOrderAmount ?: 0L,
            expiredAt = expiredAt,
        )
    }

    data class UpdateCouponRequest(
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long? = null,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(id: Long): UpdateCouponCommand = UpdateCouponCommand(
            id = id,
            name = name,
            type = parseType(type),
            value = value,
            minOrderAmount = minOrderAmount ?: 0L,
            expiredAt = expiredAt,
        )
    }

    data class CouponResponse(
        val id: Long,
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long,
        val expiredAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: CouponResult): CouponResponse = CouponResponse(
                id = result.id,
                name = result.name,
                type = result.type.name,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                expiredAt = result.expiredAt,
            )
        }
    }

    companion object {
        private fun parseType(raw: String): CouponType = when (raw.uppercase()) {
            "FIXED" -> CouponType.FIXED
            "RATE" -> CouponType.RATE
            else -> throw CoreException(
                ErrorType.BAD_REQUEST,
                "유효하지 않은 쿠폰 타입입니다: '$raw' (사용 가능한 값: FIXED, RATE)",
            )
        }
    }
}
