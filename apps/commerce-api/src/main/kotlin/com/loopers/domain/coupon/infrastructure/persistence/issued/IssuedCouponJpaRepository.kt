package com.loopers.domain.coupon.infrastructure.persistence.issued

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IssuedCouponJpaRepository : JpaRepository<IssuedCouponJpaEntity, Long> {
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IssuedCouponJpaEntity c where c.id = :issuedCouponId")
    fun findByIdForUpdate(
        @Param("issuedCouponId") issuedCouponId: Long,
    ): IssuedCouponJpaEntity?

    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<IssuedCouponJpaEntity>

    fun findByCouponTemplateIdOrderByIssuedAtDesc(
        couponTemplateId: Long,
        pageable: Pageable,
    ): List<IssuedCouponJpaEntity>
}
