package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.EventCoupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventCouponJpaRepository : JpaRepository<EventCoupon, Long> {
    fun findByIdAndDeletedAtIsNull(couponId: Long): EventCoupon?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update event_coupons ec
           set ec.issued_quantity = ec.issued_quantity + 1
         where ec.coupon_id = :couponId
           and ec.issued_quantity < ec.total_quantity
           and exists (
               select 1
                 from coupons c
                where c.id = ec.coupon_id
                  and c.deleted_at is null
           )
        """,
        nativeQuery = true,
    )
    fun reserveOneIfAvailable(@Param("couponId") couponId: Long): Int
}
