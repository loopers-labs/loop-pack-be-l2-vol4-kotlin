package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon {
        return CouponMapper.toEntity(coupon)
            .let(couponJpaRepository::save)
            .let(CouponMapper::toDomain)
    }

    override fun existsByName(name: String): Boolean {
        return couponJpaRepository.existsByName(name)
    }
}
