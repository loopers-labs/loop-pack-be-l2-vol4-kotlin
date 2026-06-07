package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import java.time.LocalDateTime

/**
 * 내 쿠폰 목록 조회 전용 결과. 발급 쿠폰([UserCoupon])과 그 템플릿([CouponTemplate]) 정보를 조립한다.
 * [status] 는 조회 시점 기준으로 만료를 반영한 "표시용 상태"다(DB 의 저장 상태와 다를 수 있음).
 */
data class MyCouponResult(
    val id: Long,
    val couponTemplateId: Long,
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    val status: CouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun of(userCoupon: UserCoupon, template: CouponTemplate, now: LocalDateTime): MyCouponResult {
            val displayStatus = userCoupon.expireIfExpired(template.expiredAt, now).status
            return MyCouponResult(
                id = userCoupon.id,
                couponTemplateId = template.id,
                name = template.name,
                type = template.type,
                value = template.value,
                minOrderAmount = template.minOrderAmount,
                status = displayStatus,
                issuedAt = userCoupon.issuedAt,
                usedAt = userCoupon.usedAt,
                expiredAt = template.expiredAt,
            )
        }
    }
}
