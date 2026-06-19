package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IssuedCouponJpaRepository : JpaRepository<IssuedCoupon, Long> {
    fun findByUserIdAndCouponIdAndDeletedAtIsNull(userId: Long, couponId: Long): IssuedCoupon?

    fun existsByUserIdAndCouponIdAndDeletedAtIsNull(userId: Long, couponId: Long): Boolean

    fun findAllByUserIdAndDeletedAtIsNullOrderByIdDesc(userId: Long): List<IssuedCoupon>

    fun findAllByCouponIdAndDeletedAtIsNullOrderByIdDesc(couponId: Long, pageable: Pageable): List<IssuedCoupon>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update IssuedCoupon issue
           set issue.status = :usedStatus
         where issue.userId = :userId
           and issue.couponId = :couponId
           and issue.status = :availableStatus
           and issue.deletedAt is null
        """,
    )
    fun markUsedIfAvailable(
        @Param("userId") userId: Long,
        @Param("couponId") couponId: Long,
        @Param("availableStatus") availableStatus: IssuedCouponStatus,
        @Param("usedStatus") usedStatus: IssuedCouponStatus,
    ): Int
}
