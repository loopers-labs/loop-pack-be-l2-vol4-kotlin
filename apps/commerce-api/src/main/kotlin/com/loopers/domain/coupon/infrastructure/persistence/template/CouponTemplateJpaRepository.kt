package com.loopers.domain.coupon.infrastructure.persistence.template

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): CouponTemplateJpaEntity?
}
