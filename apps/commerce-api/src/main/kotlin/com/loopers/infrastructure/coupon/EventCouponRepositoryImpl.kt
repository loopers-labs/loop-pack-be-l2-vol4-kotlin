package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.coupon.EventCouponRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class EventCouponRepositoryImpl(
    private val eventCouponJpaRepository: EventCouponJpaRepository,
) : EventCouponRepository {
    override fun save(eventCoupon: EventCoupon): EventCoupon =
        eventCouponJpaRepository.save(eventCoupon)

    override fun findByCouponId(couponId: Long): EventCoupon? =
        eventCouponJpaRepository.findByIdAndDeletedAtIsNull(couponId)

    @Transactional
    override fun reserveOneIfAvailable(couponId: Long): Boolean =
        eventCouponJpaRepository.reserveOneIfAvailable(couponId) == 1
}
