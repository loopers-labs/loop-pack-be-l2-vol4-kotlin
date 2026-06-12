package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.IssuedCouponStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class IssuedCouponRepositoryImpl(
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
) : IssuedCouponRepository {
    override fun save(issuedCoupon: IssuedCoupon): IssuedCoupon =
        issuedCouponJpaRepository.save(issuedCoupon)

    override fun findByUserIdAndCouponId(userId: Long, couponId: Long): IssuedCoupon? =
        issuedCouponJpaRepository.findByUserIdAndCouponIdAndDeletedAtIsNull(userId, couponId)

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean =
        issuedCouponJpaRepository.existsByUserIdAndCouponIdAndDeletedAtIsNull(userId, couponId)

    override fun findByUserId(userId: Long): List<IssuedCoupon> =
        issuedCouponJpaRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdDesc(userId)

    override fun findByCouponId(couponId: Long, page: Int, size: Int): List<IssuedCoupon> =
        issuedCouponJpaRepository.findAllByCouponIdAndDeletedAtIsNullOrderByIdDesc(couponId, PageRequest.of(page, size))

    override fun markUsedIfAvailable(userId: Long, couponId: Long): Boolean =
        issuedCouponJpaRepository.markUsedIfAvailable(
            userId = userId,
            couponId = couponId,
            availableStatus = IssuedCouponStatus.AVAILABLE,
            usedStatus = IssuedCouponStatus.USED,
        ) == 1
}
