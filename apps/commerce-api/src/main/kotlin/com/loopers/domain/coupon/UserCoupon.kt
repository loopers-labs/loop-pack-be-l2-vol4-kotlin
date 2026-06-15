package com.loopers.domain.coupon

import java.time.LocalDateTime

/**
 * 사용자에게 발급된 쿠폰.
 * [couponTemplateId] 로 [CouponTemplate] 을 참조하며, 상태머신(AVAILABLE/USED/EXPIRED)을 가진다.
 * "최대 1회 사용" 불변식을 이 객체에 캡슐화한다.
 */
data class UserCoupon(
    val id: Long = 0L,
    val couponTemplateId: Long,
    val userId: Long,
    val status: CouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime? = null,
) {
    init {
        require(couponTemplateId > 0) { "쿠폰 템플릿 ID는 0보다 커야 합니다." }
        require(userId > 0) { "회원 ID는 0보다 커야 합니다." }
        require((status == CouponStatus.USED) == (usedAt != null)) {
            "사용 완료 상태일 때만 사용 시각이 존재해야 합니다."
        }
    }

    /**
     * 쿠폰을 사용한다. AVAILABLE 상태에서만 가능하며, 그 외 상태(USED/EXPIRED)에서는 예외.
     * "최대 1회 사용" 불변식의 캡슐화 지점.
     */
    fun use(now: LocalDateTime): UserCoupon {
        require(status == CouponStatus.AVAILABLE) {
            "사용할 수 없는 쿠폰입니다: $status"
        }
        return copy(status = CouponStatus.USED, usedAt = now)
    }

    /**
     * 사용 완료(USED) 쿠폰을 다시 사용 가능(AVAILABLE)으로 되돌린다.
     * 주문 결제 실패 등으로 쿠폰 사용을 취소(보상)할 때 사용한다. USED 상태에서만 가능.
     */
    fun restore(): UserCoupon {
        require(status == CouponStatus.USED) {
            "사용 완료 상태에서만 복원할 수 있습니다: $status"
        }
        return copy(status = CouponStatus.AVAILABLE, usedAt = null)
    }

    /**
     * 현재 시각 기준으로 만료 여부를 확인해, 만료되었으면 EXPIRED 로 전이한 쿠폰을 반환한다.
     * AVAILABLE 이면서 [expiredAt] 을 지난 경우에만 전이하고, 그 외에는 그대로 반환한다(멱등, 예외 없음).
     */
    fun expireIfExpired(expiredAt: LocalDateTime, now: LocalDateTime): UserCoupon {
        if (status != CouponStatus.AVAILABLE) return this
        if (!now.isAfter(expiredAt)) return this
        return copy(status = CouponStatus.EXPIRED)
    }

    /** 해당 사용자의 소유 쿠폰인지 검증한다. */
    fun belongsTo(userId: Long): Boolean = this.userId == userId

    companion object {
        fun issue(
            couponTemplateId: Long,
            userId: Long,
            issuedAt: LocalDateTime,
        ): UserCoupon = UserCoupon(
            couponTemplateId = couponTemplateId,
            userId = userId,
            status = CouponStatus.AVAILABLE,
            issuedAt = issuedAt,
            usedAt = null,
        )
    }
}
