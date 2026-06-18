package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: CouponModel): CouponModel {
        return couponJpaRepository.save(coupon)
    }

    override fun findActiveById(id: Long): CouponModel? {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAllActive(pageable: Pageable): Page<CouponModel> {
        return couponJpaRepository.findAllByDeletedAtIsNull(pageable)
    }

    override fun findAllByIdIn(ids: List<Long>): List<CouponModel> {
        return couponJpaRepository.findAllByIdIn(ids)
    }
}
