package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon {
        try {
            return CouponMapper.toEntity(coupon)
                .let(couponJpaRepository::save)
                .let(CouponMapper::toDomain)
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "Coupon already exists.")
        }
    }

    override fun existsByName(name: String): Boolean {
        return couponJpaRepository.existsByName(name)
    }
}
