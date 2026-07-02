package com.loopers.infrastructure.coupon.repository

import com.loopers.infrastructure.coupon.entity.CouponEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponJpaRepository : JpaRepository<CouponEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coupon from CouponEntity coupon where coupon.id = :couponId")
    fun findByIdForUpdate(@Param("couponId") couponId: Long): CouponEntity?

    fun findAllByIsDeletedFalse(pageable: Pageable): Page<CouponEntity>

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: Long): Boolean
}
