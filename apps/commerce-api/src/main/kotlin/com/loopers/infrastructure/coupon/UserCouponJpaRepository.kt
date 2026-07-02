package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserCouponJpaRepository : JpaRepository<UserCouponModel, Long> {
    fun findAllByCoupon_Id(couponId: Long, pageable: Pageable): Page<UserCouponModel>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): UserCouponModel?

    @Query("SELECT uc FROM UserCouponModel uc JOIN FETCH uc.coupon WHERE uc.userId = :userId")
    fun findByUserId(userId: Long): List<UserCouponModel>

    fun countAllByCoupon_Id(couponId: Long): Long

    fun existsByCoupon_IdAndUserId(couponId: Long, userId: Long): Boolean
}
