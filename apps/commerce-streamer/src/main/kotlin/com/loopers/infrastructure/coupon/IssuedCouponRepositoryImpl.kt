package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCouponModel
import com.loopers.domain.coupon.IssuedCouponRepository
import org.springframework.stereotype.Repository

@Repository
class IssuedCouponRepositoryImpl(
    private val jpaRepository: IssuedCouponJpaRepository,
) : IssuedCouponRepository {

    override fun save(model: IssuedCouponModel): IssuedCouponModel {
        return jpaRepository.save(model)
    }

    override fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean {
        return jpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)
    }
}
