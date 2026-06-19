package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon = couponJpaRepository.save(coupon)

    override fun findById(couponId: Long): Coupon? =
        couponJpaRepository.findByIdAndDeletedAtIsNull(couponId)

    override fun findAll(page: Int, size: Int): List<Coupon> =
        couponJpaRepository.findAllByDeletedAtIsNullOrderByIdDesc(PageRequest.of(page, size))
}
