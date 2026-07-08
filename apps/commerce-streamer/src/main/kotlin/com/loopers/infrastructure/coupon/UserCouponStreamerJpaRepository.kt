package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface UserCouponStreamerJpaRepository : JpaRepository<UserCouponStreamerEntity, Long> {
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
}
