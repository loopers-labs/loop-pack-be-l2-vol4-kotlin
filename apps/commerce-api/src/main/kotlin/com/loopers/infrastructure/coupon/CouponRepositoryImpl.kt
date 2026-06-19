package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon {
        val entity = coupon.id
            ?.let { id -> couponJpaRepository.findByIdAndDeletedAtIsNull(id) }
            ?.also { it.updateFrom(coupon) }
            ?: CouponJpaEntity.from(coupon)

        return couponJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Coupon? {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id)?.toDomain()
    }
}
