package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepositoryPort
import org.springframework.stereotype.Component

@Component
class UserCouponRepositoryAdapter(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepositoryPort {
    override fun save(userCoupon: UserCoupon): UserCoupon =
        userCouponJpaRepository.save(UserCouponEntity.from(userCoupon)).toDomain()

    override fun findById(id: Long): UserCoupon? =
        userCouponJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean =
        userCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)

    override fun findAllByUserId(userId: Long): List<UserCoupon> =
        userCouponJpaRepository.findAllByUserIdOrderByIdDesc(userId).map { it.toDomain() }
}
