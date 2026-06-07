package com.loopers.domain.coupon

interface UserCouponRepositoryPort {
    fun save(userCoupon: UserCoupon): UserCoupon
    fun findById(id: Long): UserCoupon?
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean

    /** 해당 사용자가 발급받은 쿠폰 전체를 최근 발급순(id 내림차순)으로 반환한다. */
    fun findAllByUserId(userId: Long): List<UserCoupon>
}
