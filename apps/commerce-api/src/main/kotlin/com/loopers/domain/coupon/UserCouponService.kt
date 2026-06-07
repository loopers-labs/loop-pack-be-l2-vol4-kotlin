package com.loopers.domain.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDateTime

/**
 * 발급 쿠폰 도메인 서비스 (Spring 어노테이션 없음).
 * 빈 등록은 application/coupon/CouponConfig 에서 수행한다.
 */
class UserCouponService(
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
) {
    fun getById(id: Long): UserCoupon =
        userCouponRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다.")

    /** 해당 사용자가 발급받은 쿠폰 전체를 최근 발급순으로 반환한다. */
    fun getByUserId(userId: Long): List<UserCoupon> =
        userCouponRepositoryPort.findAllByUserId(userId)

    /** 해당 쿠폰 템플릿의 발급 내역을 최근 발급순으로 페이지 조회한다. */
    fun getByCouponTemplateId(couponTemplateId: Long, pageRequest: PageRequest): PageResult<UserCoupon> =
        userCouponRepositoryPort.findAllByCouponTemplateId(couponTemplateId, pageRequest)

    /**
     * 쿠폰 템플릿을 사용자에게 발급한다(AVAILABLE).
     * - 만료된 템플릿은 발급할 수 없다(BAD_REQUEST).
     * - 1인 1매 정책: 동일 사용자가 동일 템플릿을 이미 보유하면 발급할 수 없다(CONFLICT).
     */
    fun issue(template: CouponTemplate, userId: Long, now: LocalDateTime): UserCoupon {
        if (now.isAfter(template.expiredAt)) {
            throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.")
        }
        if (userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(userId, template.id)) {
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")
        }
        val userCoupon = UserCoupon.issue(
            couponTemplateId = template.id,
            userId = userId,
            issuedAt = now,
        )
        return userCouponRepositoryPort.save(userCoupon)
    }
}
