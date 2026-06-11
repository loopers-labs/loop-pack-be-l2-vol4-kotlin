package com.loopers.coupon.infrastructure

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
class CouponRepositoryImpl(
    val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon = couponJpaRepository.save(coupon)
}

interface CouponJpaRepository : JpaRepository<Coupon, Long>
