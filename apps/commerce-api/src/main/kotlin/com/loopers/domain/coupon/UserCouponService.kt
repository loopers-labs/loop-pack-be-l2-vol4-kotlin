package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 발급 쿠폰 도메인 서비스 (Spring 어노테이션 없음).
 * 빈 등록은 application/coupon/CouponConfig 에서 수행한다 (후속 태스크).
 */
class UserCouponService(
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
) {
    fun getById(id: Long): UserCoupon =
        userCouponRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다.")
}
