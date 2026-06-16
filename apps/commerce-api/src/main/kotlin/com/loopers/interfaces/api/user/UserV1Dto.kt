package com.loopers.interfaces.api.user

import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.application.user.UserInfo
import com.loopers.domain.coupon.CouponIssueDisplayStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.user.UserSignUpCommand
import java.time.LocalDate
import java.time.ZonedDateTime

class UserV1Dto {
    data class SignUpRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        fun toCommand(): UserSignUpCommand {
            return UserSignUpCommand(
                loginId = loginId,
                rawPassword = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }

    data class SignUpResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): SignUpResponse {
                return SignUpResponse(
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }

    data class GetMeResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): GetMeResponse {
                return GetMeResponse(
                    loginId = info.loginId,
                    name = info.name.dropLast(1) + "*",
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }

    data class UpdatePasswordRequest(
        val newPassword: String,
    )

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
