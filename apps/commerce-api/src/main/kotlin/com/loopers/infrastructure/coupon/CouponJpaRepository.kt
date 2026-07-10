package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponJpaRepository : JpaRepository<CouponModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponModel?
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<CouponModel>
    fun findAllByIdIn(ids: List<Long>): List<CouponModel>

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE CouponModel c SET c.issuedCount = c.issuedCount + 1
        WHERE c.id = :couponId
          AND c.deletedAt IS NULL
          AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity)
        """,
    )
    fun claimIssueSlot(@Param("couponId") couponId: Long): Int
}
