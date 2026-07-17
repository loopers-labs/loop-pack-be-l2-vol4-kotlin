package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplateModel
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponTemplateModel c WHERE c.id = :id")
    fun findByIdWithLock(id: Long): CouponTemplateModel?
}
